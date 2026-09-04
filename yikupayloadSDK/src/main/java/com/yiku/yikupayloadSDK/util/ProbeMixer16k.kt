package com.yiku.yikupayloadSDK.util

import kotlin.math.pow

// PN 叠加器，用于在音频上叠加PN，通过探测PN来判断延迟
class ProbeMixer16k {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val PROBE_INTERVAL_MS = 2000    // 每 2 秒探测一次
        private const val PROBE_DURATION_MS = 40      // 每次探测 40ms
        private const val PROBE_DB_SCALE = -25        // dB 幅度（相对满量程）
        // 暴露 PN 序列给录音端做互相关
        @Volatile private var instance: ProbeMixer16k? = null
        fun getProbeSequence(): ShortArray {
            if (instance == null) {
                instance = ProbeMixer16k()
            }
            return instance!!.probeSeq.copyOf()
        }
    }

    private val probeSeq: ShortArray  // 40ms @ 16kHz = 640 samples
    private val probeScale: Int

    private var sampleCounter = 0L
    private var isProbing = false
    private var probeSeqOffset = 0

    private val probeIntervalSamples: Int = SAMPLE_RATE * PROBE_INTERVAL_MS / 1000   // 32000
    private val probeDurationSamples: Int = SAMPLE_RATE * PROBE_DURATION_MS / 1000   // 640

    init {
        probeSeq = generateGoldSequence(probeDurationSamples)
        probeScale = (32768 * 10.0.pow(PROBE_DB_SCALE / 20.0)).toInt()
    }

    fun mix(pcm16k: ShortArray): ShortArray {
        for (i in pcm16k.indices) {
            if (!isProbing && sampleCounter > 0 &&
                sampleCounter % probeIntervalSamples == 0L) {
                isProbing = true
                probeSeqOffset = 0
            }

            if (isProbing) {
                val pnSample = probeSeq[probeSeqOffset].toInt() * probeScale
                val sum = pcm16k[i] + pnSample
                if (sum in -32768..32767) {
                    pcm16k[i] = sum.toShort()
                }
                // else: 跳过本次叠加，不 clip
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