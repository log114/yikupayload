package com.yiku.yikupayloadSDK.util

import android.util.Log

class HardwareNoiseSuppressor(private val audioSessionId: Int) {
    private var noiseSuppressor: android.media.audiofx.NoiseSuppressor? = null

    fun enable() {
        // 检查设备是否支持硬件噪声抑制
        if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = android.media.audiofx.NoiseSuppressor.create(audioSessionId)
                noiseSuppressor?.enabled = true

                Log.d("HardwareNoiseSuppressor", "硬件噪声抑制已启用")
            } catch (e: Exception) {
                Log.e("HardwareNoiseSuppressor", "启用硬件噪声抑制失败: ${e.message}")
            }
        } else {
            Log.w("HardwareNoiseSuppressor", "设备不支持硬件噪声抑制")
        }
    }

    fun release() {
        noiseSuppressor?.release()
        noiseSuppressor = null
    }
}