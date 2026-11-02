package com.yiku.yikupayloadSDK.util

import kotlin.math.pow

class ProbeMixer8k {

    companion object {
        private const val SAMPLE_RATE = 8000
        private const val PROBE_INTERVAL_MS = 5000
        private const val PROBE_DURATION_MS = 40
        private const val PROBE_DB_SCALE = -15

        @Volatile
        var lastPnSendTimeMs: Long = 0L
            private set

        val PROBE_SEQ_8K: ShortArray = generateGoldSequence(
            SAMPLE_RATE * PROBE_DURATION_MS / 1000 // 320
        )

        private val PROBE_SCALE: Int =
            (32768 * 10.0.pow(PROBE_DB_SCALE / 20.0)).toInt()

        private fun generateGoldSequence(length: Int): ShortArray {
            val seq = ShortArray(length)
            var lfsr = 0xACE1u.toInt()
            for (i in seq.indices) {
                val feedback =
                    ((lfsr shr 0) xor (lfsr shr 2) xor (lfsr shr 3) xor (lfsr shr 5)) and 1
                lfsr = ((lfsr shr 1) or (feedback shl 15)) and 0xFFFF
                seq[i] = (((lfsr and 1) * 2 - 1).toShort())
            }
            return seq
        }

        fun getProbeSequence(): ShortArray = PROBE_SEQ_8K.copyOf()
    }

    private var sampleCounter = 0L
    private var isProbing = false
    private var probeSeqOffset = 0

    private val probeIntervalSamples =
        SAMPLE_RATE * PROBE_INTERVAL_MS / 1000   // 80000
    private val probeDurationSamples =
        SAMPLE_RATE * PROBE_DURATION_MS / 1000   // 320

    fun mix(pcm8k: ShortArray): ShortArray {
        for (i in pcm8k.indices) {

            if (!isProbing && (
                        (sampleCounter > 0 && sampleCounter % probeIntervalSamples == 0L) ||
                                (sampleCounter == 0L && i == 0)
                        )) {
                isProbing = true
                probeSeqOffset = 0
                lastPnSendTimeMs = System.currentTimeMillis()
            }

            if (isProbing) {
                val pnSample = PROBE_SEQ_8K[probeSeqOffset].toInt() * PROBE_SCALE
                val sum = pcm8k[i] + pnSample
                pcm8k[i] = sum.coerceIn(-32768, 32767).toShort()
                probeSeqOffset++
                if (probeSeqOffset >= probeDurationSamples) {
                    isProbing = false
                }
            }
            sampleCounter++
        }
        return pcm8k
    }
}