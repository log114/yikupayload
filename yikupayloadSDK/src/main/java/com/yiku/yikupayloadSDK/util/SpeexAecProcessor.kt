package com.yiku.yikupayloadSDK.util

class SpeexAecProcessor(
    sampleRate: Int = 8000,
    frameSize: Int = 80,
    filterMs: Int = 500,
    enableAgc: Boolean = true
) {
    private val filterLength = sampleRate * filterMs / 1000
    private val handle: Long = nativeCreate(
        sampleRate, frameSize, filterLength, if (enableAgc) 1 else 0
    )

    fun playback(far: ShortArray) = nativePlayback(handle, far, far.size)
    fun process(near: ShortArray): ShortArray = nativeProcess(handle, near, null, 0)
    fun release() { if (handle != 0L) nativeRelease(handle) }

    private external fun nativeCreate(rate: Int, frame: Int, filter: Int, agc: Int): Long
    private external fun nativePlayback(h: Long, play: ShortArray, len: Int)
    private external fun nativeProcess(h: Long, near: ShortArray, far: ShortArray?, ms: Int): ShortArray
    private external fun nativeRelease(h: Long)

    companion object { init { System.loadLibrary("speexaec") } }
}