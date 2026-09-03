package com.yiku.yikupayloadSDK.util

import java.util.concurrent.ConcurrentLinkedQueue

class FarendProvider {
    // 线程安全队列，存放 8kHz 的 10ms 帧（80 个 short）
    data class TimedFrame(val pcm: ShortArray, val enqueueTimeMs: Long)
    private val queue = ConcurrentLinkedQueue<TimedFrame>()
    private val MAX_QUEUE_SIZE = 20  // 防止堆积

    /**
     * 播放 16kHz PCM 时调用
     * pcm16k: 即将送给 AudioTrack 的 16kHz 数据
     */
    fun onBeforePlay(pcm16k: ShortArray) {
        val pcm8k = Uilts.downsample16kTo8k(pcm16k)
        for (i in 0 until pcm8k.size step 80) {
            val end = minOf(i + 80, pcm8k.size)
            val frame = ShortArray(80)
            pcm8k.copyInto(frame, 0, i, end)
            // 队列满时丢弃最旧的帧（防止堆积）
            if (queue.size >= MAX_QUEUE_SIZE) {
                queue.poll()  // 丢弃旧帧
            }
            queue.offer(TimedFrame(frame, System.currentTimeMillis()))
        }
    }

    /** 录音端调用，拉取一帧参考信号 */
    fun pollFarendFrame(): TimedFrame? = queue.poll()

    /** ★ 清空队列（录音开始 / 播放停止时调用） */
    fun clear() {
        queue.clear()
    }
}