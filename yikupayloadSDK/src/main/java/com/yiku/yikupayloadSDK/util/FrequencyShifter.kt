package com.yiku.yikupayloadSDK.util

/**
 * 5Hz 移频器（基于希尔伯特变换）
 * 把音频整体频率搬移几个 Hz，破坏啸叫的正反馈相位条件
 */
class FrequencyShifter(
    private val sampleRate: Int = 8000,
    private val shiftHz: Float = 5f
) {
    private val hilbertSize = 64
    private val hilbertCoeffs = FloatArray(hilbertSize)
    private val delayLine = FloatArray(hilbertSize)
    private var delayIndex = 0

    private var phase = 0f
    private val phaseInc = 2f * Math.PI.toFloat() * shiftHz / sampleRate

    init {
        // 生成希尔伯特变换 FIR 系数（带汉宁窗）
        for (i in 0 until hilbertSize) {
            val n = i - hilbertSize / 2f
            hilbertCoeffs[i] = if (n != 0f) {
                val window = 0.54f - 0.46f * kotlin.math.cos(2f * Math.PI.toFloat() * i / hilbertSize)
                val coeff = (2f / Math.PI.toFloat()) * kotlin.math.sin(Math.PI.toFloat() * n / 2f).let { it * it / n }
                coeff * window
            } else 0f
        }
    }

    fun process(samples: ShortArray) {
        for (i in samples.indices) {
            val x = samples[i] / 32768f

            // 写入延迟线
            delayLine[delayIndex] = x
            // 希尔伯特变换（卷积）
            var hilbertOut = 0f
            for (j in hilbertCoeffs.indices) {
                val idx = (delayIndex - j + hilbertSize) % hilbertSize
                hilbertOut += hilbertCoeffs[j] * delayLine[idx]
            }
            delayIndex = (delayIndex + 1) % hilbertSize

            // 正交混频：x*cos - x_h*sin  → 频率搬移
            val cosP = kotlin.math.cos(phase)
            val sinP = kotlin.math.sin(phase)
            val shifted = x * cosP - hilbertOut * sinP

            // 更新相位
            phase += phaseInc
            if (phase > 2f * Math.PI.toFloat()) phase -= 2f * Math.PI.toFloat()

            // 写回
            val out = (shifted * 32768f).toInt()
            samples[i] = out.coerceIn(-32768, 32767).toShort()
        }
    }

    fun reset() {
        delayLine.fill(0f)
        delayIndex = 0
        phase = 0f
    }
}