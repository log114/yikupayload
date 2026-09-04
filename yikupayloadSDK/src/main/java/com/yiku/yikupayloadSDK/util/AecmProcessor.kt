package com.yiku.yikupayloadSDK.util

class AecmProcessor(
    private val sampleRate: Int = 8000,
    private val echoMode: Int = 4
) {
    companion object {
        init {
            System.loadLibrary("aecm")
        }
    }

    private var nativeInst: Long = 0

    init {
        nativeInst = nativeCreate()
        if (nativeInst == 0L) {
            throw OutOfMemoryError("AECM instance creation failed")
        }
        val ret = nativeInit(nativeInst, sampleRate)
        if (ret != 0) {
            throw IllegalStateException("AECM init failed: $ret")
        }
    }

    /** 帧长：8kHz 下 10ms = 80 个 short */
    val frameLength: Int = sampleRate / 100

    /** 喂远端参考帧（扬声器播放的 8kHz PCM） */
    fun bufferFarend(far: ShortArray, samples: Int = frameLength) {
        nativeBufferFarend(nativeInst, far, samples)
    }

    /** 处理近端帧（麦克风录到的 8kHz PCM），输出去回声后的 PCM */
    fun process(near: ShortArray, msInSndCardBuf: Int = 120): ShortArray {
        val out = ShortArray(frameLength)
        nativeProcess(nativeInst, near, out, frameLength, msInSndCardBuf)
        return out
    }

    fun release() {
        if (nativeInst != 0L) {
            nativeFree(nativeInst)
            nativeInst = 0
        }
    }

    // JNI 声明
    private external fun nativeCreate(): Long
    private external fun nativeInit(inst: Long, sampleRate: Int): Int
    private external fun nativeBufferFarend(inst: Long, far: ShortArray, samples: Int)
    private external fun nativeProcess(
        inst: Long, near: ShortArray, out: ShortArray,
        samples: Int, msInSndCardBuf: Int
    )
    private external fun nativeFree(inst: Long)
}