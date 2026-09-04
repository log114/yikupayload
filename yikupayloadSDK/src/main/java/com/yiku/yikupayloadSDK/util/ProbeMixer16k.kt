package com.yiku.yikupayloadSDK.util

// PN 叠加器，用于在音频上叠加PN，通过探测PN来判断延迟
class ProbeMixer16k {
    private val probeSeq = generateGoldSequence(320)  // 40ms @ 16kHz
    private var probeCounter = 0
    private var probeRemaining = 0
    private val PROBE_INTERVAL = 2000 / 10  // 2秒 / 10ms帧
    private val PROBE_DURATION = 40 / 10    // 40ms / 10ms帧

    fun mix(pcm16k: ShortArray): ShortArray {
        probeCounter++
        if (probeCounter >= PROBE_INTERVAL) {
            probeRemaining = PROBE_DURATION
            probeCounter = 0
        }

        if (probeRemaining <= 0) return pcm16k  // 非探测期，原样返回

        // 叠加 PN（-30dB ≈ 1/32）
        val mixed = ShortArray(pcm16k.size)
        val probeScale = 1024  // 32768 / 32
        for (i in pcm16k.indices) {
            val pnIdx = (i + (PROBE_DURATION - probeRemaining) * 160) % probeSeq.size
            mixed[i] = (pcm16k[i] + probeSeq[pnIdx] * probeScale).coerceIn(-32768, 32767).toShort()
        }
        probeRemaining--
        return mixed
    }

    private fun generateGoldSequence(length: Int): ShortArray {
        // Gold 序列生成（和之前一样）
        val seq = ShortArray(length)
        var lfsr = 0xACE1u.toInt()
        for (i in seq.indices) {
            var bit = 0
            repeat(16) {
                bit = bit xor (lfsr and 1)
                val feedback = ((lfsr shr 0) xor (lfsr shr 2) xor (lfsr shr 3) xor (lfsr shr 5)) and 1
                lfsr = ((lfsr shr 1) or (feedback shl 15)) and 0xFFFF
            }
            seq[i] = (bit * 2 - 1).toShort()
        }
        return seq
    }
}