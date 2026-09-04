package com.yiku.yikupayloadSDK.util

import android.util.Log
import kotlin.math.sqrt

class DynamicDelayEstimator {
    companion object {
        private const val SAMPLE_RATE = 8000
        private const val SEARCH_MIN_MS = 50
        private const val SEARCH_MAX_MS = 250
        private const val WINDOW_MS = 300          // 保持 300ms 够了
        private const val PEAK_RATIO_THRESHOLD = 1.2  // 先放宽
        private const val SILENCE_THRESHOLD = 2_000_000L

        // ★ PN 回声到达窗口：播放后 50~250ms
        private const val PN_ECHO_MIN_DELAY = 50L
        private const val PN_ECHO_MAX_DELAY = 250L

        // ★ 检测频率：每 100ms 检查一次
        private const val CHECK_INTERVAL_MS = 100L
        private const val ALPHA = 0.3f
    }

    private val nearWindow = ShortArray(SAMPLE_RATE * WINDOW_MS / 1000)
    private var nearWriteIdx = 0
    private var nearFilled = false

    private val probeTemplate8k: ShortArray

    @Volatile var currentDelay: Int = 120
        private set

    private var estimationCount = 0
    private var lastDelay = 120

    // ★ 上次检查时间
    private var lastCheckTime = 0L
    // ★ 本次 PN 周期是否已经检测过了
    private var currentPnCycleDetected = false
    private var currentPnCycleStartTime = 0L

    init {
        val probeSeq16k = ProbeMixer16k.getProbeSequence()
        probeTemplate8k = Uilts.downsample16kTo8k(probeSeq16k)
    }

    fun feedNear(nearFrame: ShortArray) {
        for (s in nearFrame) {
            nearWindow[nearWriteIdx] = s
            nearWriteIdx = (nearWriteIdx + 1) % nearWindow.size
        }
        if (nearWriteIdx >= nearWindow.size - nearFrame.size) {
            nearFilled = true
        }
    }

    /**
     * ★ 每 100ms 调用一次，只在 PN 回声窗口内做检测
     */
    fun estimateOnce(): Int? {
        if (!nearFilled) return null

        val now = System.currentTimeMillis()

        // ★ 检查是否到了检测时间
        if (now - lastCheckTime < CHECK_INTERVAL_MS) {
            return null  // 还没到下次检查
        }
        lastCheckTime = now

        // ★ 检查是否在 PN 回声窗口内
        val pnPlayTime = ProbeMixer16k.lastPnPlayTimeMs
        if (pnPlayTime == 0L) return null

        val elapsedSincePn = now - pnPlayTime

        // PN 回声应该在 50~250ms 后到达
        if (elapsedSincePn < PN_ECHO_MIN_DELAY) {
            return null  // 还没到，太早了
        }
        if (elapsedSincePn > PN_ECHO_MAX_DELAY) {
            // 已经过了这个 PN 周期的回声窗口
            if (currentPnCycleStartTime != pnPlayTime) {
                // 新的 PN 周期还没开始检测
                currentPnCycleStartTime = pnPlayTime
                currentPnCycleDetected = false
            }
            if (currentPnCycleDetected) {
                return null  // 已经检测过了
            }
            // 如果 elapsed > 250ms 且还没检测过，说明这次可能漏了
            // 但仍然尝试检测（窗口里可能还有残余）
            if (elapsedSincePn > PN_ECHO_MAX_DELAY + 100) {
                return null
            }
        }

        // ★ 在回声窗口内，执行互相关
        val minLag = SEARCH_MIN_MS * 8
        val maxLag = SEARCH_MAX_MS * 8
        val probeLen = probeTemplate8k.size

        var bestLag = 0
        var bestCorr = 0.0
        var secondBest = 0.0

        for (lag in minLag until maxLag) {
            var corr = 0.0
            var normNear = 0.0
            var normProbe = 0.0

            for (i in 0 until probeLen) {
                val nearIdx = (nearWriteIdx - lag + i + nearWindow.size) % nearWindow.size
                val nearS = nearWindow[nearIdx].toDouble() / 32768.0
                val probeS = probeTemplate8k[i].toDouble() / 32768.0
                corr += nearS * probeS
                normNear += nearS * nearS
                normProbe += probeS * probeS
            }

            val normalized = corr / (sqrt(normNear * normProbe) + 1e-10)
            if (normalized > bestCorr) {
                secondBest = bestCorr
                bestCorr = normalized
                bestLag = lag
            }
        }

        val peakRatio = bestCorr / (secondBest + 1e-10)
        if (peakRatio < PEAK_RATIO_THRESHOLD) {
            Log.v("DelayEst", "Peak unclear: ratio=$peakRatio, best=$bestCorr")
            return null
        }

        val estimatedMs = (bestLag * 1000) / SAMPLE_RATE
        if (estimatedMs !in SEARCH_MIN_MS..SEARCH_MAX_MS) {
            Log.v("DelayEst", "Out of range: ${estimatedMs}ms")
            return null
        }

        currentPnCycleDetected = true  // ★ 标记本次 PN 已检测
        estimationCount++
        Log.i("DelayEst", "Est #$estimationCount: ${estimatedMs}ms (lag=$bestLag, ratio=$peakRatio, elapsed=${elapsedSincePn}ms)")

        return estimatedMs
    }

    fun updateDelay(newDelay: Int?) {
        if (newDelay == null) return

        val smoothed = (ALPHA * newDelay + (1 - ALPHA) * currentDelay).toInt()
            .coerceIn(SEARCH_MIN_MS, SEARCH_MAX_MS)

        if (Math.abs(smoothed - currentDelay) > 5) {
            lastDelay = currentDelay
            currentDelay = smoothed
            Log.i("DelayEst", "Delay updated: $lastDelay → $currentDelay ms")
        }
    }

    fun getDelay(): Int = currentDelay
}