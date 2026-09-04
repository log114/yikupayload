package com.yiku.yikupayloadSDK.util

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and

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
}