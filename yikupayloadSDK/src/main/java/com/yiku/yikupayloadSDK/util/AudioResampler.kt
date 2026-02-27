package com.yiku.yikupayloadSDK.util

// 重采样器
class AudioResampler {
    // 16000Hz音频重采样为8000Hz
    fun resample16000To8000(input: ShortArray): ShortArray {
        // 简单实现：每2个采样点取1个
        val output = ShortArray(input.size / 2)
        for (i in output.indices) {
            output[i] = input[i * 2]
        }
        return output
        // 生产环境应使用更高质量的重采样算法（如线性插值）
    }

    // 将8000Hz音频重采样为16000Hz
    fun resample8000To16000(input: ShortArray): ShortArray {
        val output = ShortArray(input.size * 2)
        for (i in input.indices) {
            output[i * 2] = input[i]
            output[i * 2 + 1] = input[i] // 简单插值
        }
        return output
    }
}

// 音频帧缓冲区和对齐器
class AudioFrameAligner {
    private val resampler = AudioResampler()
    private val buffer = mutableListOf<Short>()

    // 接收B设备的16000Hz，20ms帧，重采样并缓冲
    fun addBDeviceFrame(frame16000: ShortArray): ShortArray? {
        // 1. 重采样为8000Hz
        val frame8000 = resampler.resample16000To8000(frame16000) // 320 -> 160采样点

        // 2. 加入缓冲区
        buffer.addAll(frame8000.toList())

        // 3. 如果缓冲区足够一个A设备帧（480采样点），则取出
        if (buffer.size >= 480) {
            val result = ShortArray(480)
            for (i in 0 until 480) {
                result[i] = buffer[i]
            }
            // 移除已处理的数据
            buffer.subList(0, 480).clear()
            return result
        }
        return null
    }
}