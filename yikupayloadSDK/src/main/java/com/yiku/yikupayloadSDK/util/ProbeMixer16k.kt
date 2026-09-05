package com.yiku.yikupayloadSDK.util

import kotlin.math.pow

// PN 叠加器，用于在音频上叠加PN，通过探测PN来判断延迟
class ProbeMixer16k {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val PROBE_INTERVAL_MS = 10000
        private const val PROBE_DURATION_MS = 40
        private const val PROBE_DB_SCALE = -20

        // ★ 最近一次 PN 开始播放的时间（毫秒）
        @Volatile var lastPnPlayTimeMs: Long = 0L
            private set
        // ★ probeSeq 作为静态常量，全局唯一，类加载时生成一次
        val PROBE_SEQ_16K: ShortArray = generateGoldSequenceStatic(
            SAMPLE_RATE * PROBE_DURATION_MS / 1000  // 640
        )

        private val PROBE_SCALE: Int = (32768 * 10.0.pow(PROBE_DB_SCALE / 20.0)).toInt()

        // ★ 静态生成方法（不依赖实例）
        private fun generateGoldSequenceStatic(length: Int): ShortArray {
            val seq = ShortArray(length)
            var lfsr = 0xACE1u.toInt()
            for (i in seq.indices) {
                val feedback = ((lfsr shr 0) xor (lfsr shr 2) xor (lfsr shr 3) xor (lfsr shr 5)) and 1
                lfsr = ((lfsr shr 1) or (feedback shl 15)) and 0xFFFF
                seq[i] = ((lfsr and 1) * 2 - 1).toShort()
            }
            return seq
        }

        // ★ 对外暴露：直接返回静态常量
        fun getProbeSequence(): ShortArray = PROBE_SEQ_16K.copyOf()
    }

    // ===== 实例状态（只和 mix 有关）=====
    private var sampleCounter = 0L
    private var isProbing = false
    private var probeSeqOffset = 0

    private val probeIntervalSamples = SAMPLE_RATE * PROBE_INTERVAL_MS / 1000   // 160000
    private val probeDurationSamples = SAMPLE_RATE * PROBE_DURATION_MS / 1000   // 640

    fun mix(pcm16k: ShortArray): ShortArray {
        for (i in pcm16k.indices) {
            if (!isProbing && (
                (sampleCounter > 0 && sampleCounter % probeIntervalSamples == 0L) ||
                (sampleCounter == 0L && i == 0)  // ★ 第一帧立即触发
            )) {
                isProbing = true
                probeSeqOffset = 0
                lastPnPlayTimeMs = System.currentTimeMillis()
            }

            if (isProbing) {
                // ★ 用静态常量，不用实例字段
                val pnSample = PROBE_SEQ_16K[probeSeqOffset].toInt() * PROBE_SCALE
                val sum = pcm16k[i] + pnSample
                if (sum in -32768..32767) {
                    pcm16k[i] = sum.toShort()
                }
                probeSeqOffset++
                if (probeSeqOffset >= probeDurationSamples) {
                    isProbing = false
                }
            }

            sampleCounter++
        }
        return pcm16k
    }

    private fun generateGoldSequence(length: Int): ShortArray {
        val seq = ShortArray(length)
        var lfsr = 0xACE1u.toInt()
        for (i in seq.indices) {
            val feedback = ((lfsr shr 0) xor (lfsr shr 2) xor (lfsr shr 3) xor (lfsr shr 5)) and 1
            lfsr = ((lfsr shr 1) or (feedback shl 15)) and 0xFFFF
            val bit = lfsr and 1
            seq[i] = (bit * 2 - 1).toShort()
        }
        return seq
    }
}