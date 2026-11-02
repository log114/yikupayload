package com.yiku.yikupayloadSDK.util

import java.util.concurrent.ConcurrentLinkedQueue

class FarendProvider {
    data class TimedFrame(val pcm: ShortArray, val enqueueTimeMs: Long)
    private val queue = ConcurrentLinkedQueue<TimedFrame>()

    // 队列要足够大，存至少 1 秒的数据（防止标定期间堆积）
    private val MAX_QUEUE_SIZE = 100  // 100 × 10ms = 1 秒

    /** 发送前调用：pcm8k 是即将发给 B 的 PCM */
    fun onBeforeSend(pcm8k: ShortArray) {
        for (i in 0 until pcm8k.size step 80) {
            val end = minOf(i + 80, pcm8k.size)
            val frame = ShortArray(80)
            pcm8k.copyInto(frame, 0, i, end)

            if (queue.size >= MAX_QUEUE_SIZE) {
                queue.poll()
            }
            queue.offer(TimedFrame(frame, System.currentTimeMillis()))
        }
    }

    /**
     * echoOriginTimeMs = 当前录音时间 - estimatedDelay
     * estimatedDelay = A发送 → A录到回声 的总延迟
     */
    fun pollAlignedFrames(echoOriginTimeMs: Long, windowMs: Long = 80): List<ShortArray> {
        val result = mutableListOf<ShortArray>()
        val lowerBound = echoOriginTimeMs - windowMs
        val upperBound = echoOriginTimeMs + windowMs

        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val frame = iterator.next()
            when {
                frame.enqueueTimeMs in lowerBound..upperBound -> {
                    result.add(frame.pcm)
                    iterator.remove()
                }
                frame.enqueueTimeMs < lowerBound -> {
                    iterator.remove()
                }
            }
        }
        return result
    }

    fun clear() { queue.clear() }
}