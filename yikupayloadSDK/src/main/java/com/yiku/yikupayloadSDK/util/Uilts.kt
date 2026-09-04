package com.yiku.yikupayloadSDK.util

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and

object Uilts {
    /**
     * 生成"启动音效"风格的标定信号
     * 听起来像一声短促的"啾~"，实际是 chirp
     */
    fun generateLaunchChirp16k(durationMs: Long = 800): ShortArray {
        val sampleRate = 16000
        val numSamples = (sampleRate * durationMs / 1000).toInt()
        val pcm = ShortArray(numSamples)

        // 从 800Hz 扫到 3000Hz（听起来像"啾~"，不会刺耳）
        val fStart = 800.0
        val fEnd = 3000.0
        val k = (fEnd - fStart) / numSamples

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val freq = fStart + k * i

            // 淡入淡出，避免爆音
            val fadeIn = if (i < sampleRate * 0.05) {
                i.toDouble() / (sampleRate * 0.05)
            } else 1.0
            val fadeOut = if (i > numSamples - sampleRate * 0.1) {
                (numSamples - i).toDouble() / (sampleRate * 0.1)
            } else 1.0

            val sample = (Math.sin(2 * Math.PI * freq * t) * fadeIn * fadeOut * 18000)
                .toInt()
                .coerceIn(-32767, 32767)
            pcm[i] = sample.toShort()
        }
        return pcm
    }
    /**
     * 16kHz → 8kHz 简单降采样
     * 每 2 个样本取 1 个（如需更高质量可先做抗混叠低通）
     */
    fun downsample16kTo8k(input: ShortArray): ShortArray {
        val out = ShortArray(input.size / 2)
        for (i in out.indices) {
            // 简单平均，比单纯抽取效果更好
            out[i] = ((input[i * 2].toInt() + input[i * 2 + 1].toInt()) / 2)
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
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
}