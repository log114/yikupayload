package com.yiku.yikupayloadSDK.util

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and
import kotlin.math.sqrt

object Uilts {
    /**
     * 16kHz → 8kHz 简单降采样
     * 每 2 个样本取 1 个（如需更高质量可先做抗混叠低通）
     */
    fun downsample16kTo8k(input: ShortArray): ShortArray {
        val out = ShortArray(input.size / 2)
        for (i in out.indices) {
            out[i] = input[i * 2]  // ★ 抽取，不用平均
        }
        return out
    }
    fun byteArrayToShortArray(byteArray: ByteArray): ShortArray {
        val shortArray = ShortArray(byteArray.size / 2)
        ByteBuffer.wrap(byteArray).order(ByteOrder.nativeOrder()).asShortBuffer().get(shortArray)
        return shortArray
    }

    fun shortArrayToByteArray(shortArray: ShortArray): ByteArray {
        val count = shortArray.size
        val dest = ByteArray(count shl 1)
        for (i in 0 until count) {
            dest[i * 2] = ((shortArray[i] and 0xFFFF.toShort()).toLong() shr 0).toByte()
            dest[i * 2 + 1] = ((shortArray[i] and 0xFFFF.toShort()).toLong() shr 8).toByte()
        }
        return dest
    }

    fun File.normalizeExtensionToLowerCase(): String {
        val name = this.name
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0 && dotIndex < name.length - 1) {
            val baseName = name.substring(0, dotIndex)
            val ext = name.substring(dotIndex + 1)
            "$baseName.${ext.lowercase()}"
        } else {
            name
        }
    }

    data class AcfResult(val lag: Int, val normAcf: Double)

    fun rms(s: ShortArray): Double {
        var sum = 0L
        for (x in s) sum += x.toLong() * x.toLong()
        return sqrt(sum / s.size.toDouble())
    }

    fun checkHowling(pcm: ShortArray): AcfResult {
        val n = pcm.size
        var energy = 0L
        for (i in 0 until n) energy += pcm[i].toLong() * pcm[i].toLong()
        if (energy < 500_000) return AcfResult(0, 0.0)

        var bestLag = 0
        var bestDot = 0L
        for (lag in 10..160) {
            var dot = 0L
            for (i in 0 until n - lag) dot += pcm[i].toLong() * pcm[i + lag]
            if (dot > bestDot) { bestDot = dot; bestLag = lag }
        }
        val norm = bestDot.toDouble() / (energy + 1e-10)
        return AcfResult(bestLag, norm)
    }
}