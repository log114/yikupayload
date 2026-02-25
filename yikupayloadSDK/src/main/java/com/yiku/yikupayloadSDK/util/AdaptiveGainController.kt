package com.yiku.yikupayloadSDK.util

import android.util.Log

class AdaptiveGainController {
    companion object {
        private const val INITIAL_SILENCE_FRAMES = 10
        private const val MIN_GAIN = 0.3f
        private const val MAX_GAIN = 1.5f
        private const val NORMAL_GAIN = 1.0f
        private const val ATTACK_RATE = 0.1f  // 增益增加速率
        private const val RELEASE_RATE = 0.3f // 增益减小速率
    }

    private var currentGain = MIN_GAIN
    private var frameCount = 0
    private var isInitialPhase = true
    private val energyHistory = mutableListOf<Float>()
    private val ENERGY_HISTORY_SIZE = 20

    fun processAudio(input: ShortArray): ShortArray {
        frameCount++

        // 计算当前帧的能量
        val currentEnergy = calculateRMS(input)
        energyHistory.add(currentEnergy)
        if (energyHistory.size > ENERGY_HISTORY_SIZE) {
            energyHistory.removeAt(0)
        }

        // 初始化阶段：渐入增益，避免初始啸叫
        if (isInitialPhase) {
            if (frameCount < INITIAL_SILENCE_FRAMES) {
                // 线性渐入
                val progress = frameCount.toFloat() / INITIAL_SILENCE_FRAMES
                currentGain = MIN_GAIN + (NORMAL_GAIN - MIN_GAIN) * progress
                isInitialPhase = false
            }
        } else {
            // 自适应增益控制
            currentGain = adjustGain(currentEnergy)
        }

        // 应用增益
        return applyGain(input, currentGain)
    }

    private fun calculateRMS(data: ShortArray): Float {
        var sum = 0.0
        for (sample in data) {
            val normalized = sample.toFloat() / 32768.0
            sum += normalized * normalized
        }
        return (sum / data.size).toFloat()
    }

    private fun adjustGain(currentEnergy: Float): Float {
        // 计算历史平均能量
        val avgEnergy = if (energyHistory.isNotEmpty()) {
            energyHistory.average().toFloat()
        } else {
            currentEnergy
        }

        // 检测可能的啸叫（能量突然剧增）
        if (currentEnergy > avgEnergy * 5) {
            Log.w("AdaptiveGain", "检测到可能啸叫，快速降低增益")
            return (currentGain * 0.5f).coerceAtLeast(MIN_GAIN)
        }

        // 正常音量范围，缓慢调整到目标增益
        val targetGain = when {
            currentEnergy < 0.01f -> MIN_GAIN  // 静音或极小声
            currentEnergy > 0.5f -> MIN_GAIN   // 过大声音
            else -> NORMAL_GAIN                // 正常音量
        }

        // 平滑过渡到目标增益
        return if (currentGain < targetGain) {
            (currentGain + ATTACK_RATE * (targetGain - currentGain)).coerceAtMost(MAX_GAIN)
        } else {
            (currentGain + RELEASE_RATE * (targetGain - currentGain)).coerceAtLeast(MIN_GAIN)
        }
    }

    private fun applyGain(input: ShortArray, gain: Float): ShortArray {
        return if (Math.abs(gain - 1.0f) < 0.001f) {
            // 增益接近1，直接返回原数据
            input
        } else {
            ShortArray(input.size) { index ->
                val result = (input[index].toFloat() * gain).toInt()
                result.coerceIn(-32768, 32767).toShort()
            }
        }
    }

    fun reset() {
        currentGain = MIN_GAIN
        frameCount = 0
        isInitialPhase = true
        energyHistory.clear()
    }
}