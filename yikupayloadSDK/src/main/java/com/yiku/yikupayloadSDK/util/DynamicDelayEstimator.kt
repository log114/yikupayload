package com.yiku.yikupayloadSDK.util

import android.util.Log

class DynamicDelayEstimator {
    companion object {
        private const val SAMPLE_RATE = 8000
        private const val SEARCH_MIN_MS = 50      // 最小搜索延迟
        private const val SEARCH_MAX_MS = 250     // 最大搜索延迟
        private const val WINDOW_MS = 300         // 滑动窗口长度
        private const val PEAK_RATIO_THRESHOLD = 2.0
        private const val ALPHA = 0.3f            // 平滑因子
    }

    // 近端滑动窗口（存最近 300ms = 2400 samples @ 8kHz）
    private val nearWindow = ShortArray(SAMPLE_RATE * WINDOW_MS / 1000)
    private var nearWriteIdx = 0
    private var nearFilled = false

    // PN 模板（16kHz → 8kHz 降采样）
    private val probeTemplate8k: ShortArray

    // 延迟估计结果
    @Volatile var currentDelay: Int = 120
        private set

    private var estimationCount = 0
    private var lastDelay = 120

    init {
        // 从 ProbeMixer16k 拿到 16kHz 模板，降采样到 8kHz
        val probeSeq16k = ProbeMixer16k.getProbeSequence()  // 需要暴露静态方法
        probeTemplate8k = Uilts.downsample16kTo8k(probeSeq16k)
    }

    /** 喂入一帧近端数据（录音线程调用） */
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
     * 执行一次延迟估计（每 2 秒调用一次）
     * 返回新的延迟估计值(ms)，检测失败返回 null
     */
    fun estimateOnce(): Int? {
        if (!nearFilled) return null

        val minLag = SEARCH_MIN_MS * 8        // samples @ 8kHz
        val maxLag = SEARCH_MAX_MS * 8
        val probeLen = probeTemplate8k.size   // 通常 320 samples (40ms @ 8kHz)

        var bestLag = 0
        var bestCorr = 0.0
        var secondBest = 0.0

        // 归一化互相关
        for (lag in minLag until maxLag) {
            var corr = 0.0
            var normNear = 0.0
            var normProbe = 0.0

            val searchLen = minOf(probeLen, 160)
            for (i in 0 until searchLen) {
                val nearIdx = (nearWriteIdx - lag - searchLen + i + nearWindow.size) % nearWindow.size
                val nearS = nearWindow[nearIdx].toDouble() / 32768.0
                val probeS = probeTemplate8k[i].toDouble() / 32768.0
                corr += nearS * probeS
                normNear += nearS * nearS
                normProbe += probeS * probeS
            }

            val normalized = corr / (Math.sqrt(normNear * normProbe) + 1e-10)
            if (normalized > bestCorr) {
                secondBest = bestCorr
                bestCorr = normalized
                bestLag = lag
            }
        }

        // 峰值主副瓣比检查
        val peakRatio = bestCorr / (secondBest + 1e-10)
        if (peakRatio < PEAK_RATIO_THRESHOLD) {
            Log.v("DelayEst", "Peak unclear: ratio=$peakRatio, best=$bestCorr")
            return null
        }

        // lag → ms
        val estimatedMs = (bestLag * 1000) / SAMPLE_RATE

        // 合理性检查
        if (estimatedMs !in SEARCH_MIN_MS..SEARCH_MAX_MS) {
            Log.v("DelayEst", "Out of range: ${estimatedMs}ms")
            return null
        }

        estimationCount++
        Log.i("DelayEst", "Est #$estimationCount: ${estimatedMs}ms (lag=$bestLag, ratio=$peakRatio)")

        return estimatedMs
    }

    /** 平滑更新延迟 */
    fun updateDelay(newDelay: Int?) {
        if (newDelay == null) return

        // 指数平滑
        val smoothed = (ALPHA * newDelay + (1 - ALPHA) * currentDelay).toInt()
            .coerceIn(SEARCH_MIN_MS, SEARCH_MAX_MS)

        // 只有变化超过 5ms 才更新（防抖）
        if (Math.abs(smoothed - currentDelay) > 5) {
            lastDelay = currentDelay
            currentDelay = smoothed
            Log.i("DelayEst", "Delay updated: $lastDelay → $currentDelay ms")
        }
    }

    fun getDelay(): Int = currentDelay
}