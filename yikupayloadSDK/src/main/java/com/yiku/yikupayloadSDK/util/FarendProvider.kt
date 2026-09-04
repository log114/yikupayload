package com.yiku.yikupayloadSDK.util

import java.util.concurrent.ConcurrentLinkedQueue

class FarendProvider {
    data class TimedFrame(val pcm: ShortArray, val enqueueTimeMs: Long)
    private val queue = ConcurrentLinkedQueue<TimedFrame>()

    // 队列要足够大，存至少 1 秒的数据（防止标定期间堆积）
    private val MAX_QUEUE_SIZE = 100  // 100 × 10ms = 1 秒

    /** 播放端调用 */
    fun onBeforePlay(pcm16k: ShortArray) {
        val pcm8k = Uilts.downsample16kTo8k(pcm16k)
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
     * ★ 按时间窗口取帧（核心方法）
     * @param echoOriginTimeMs 回声对应的远端播放时间 = 当前录音时间 - actualDelay
     * @param windowMs 前后窗口，确保取到
     */
    fun pollAlignedFrames(echoOriginTimeMs: Long, windowMs: Long = 25): List<ShortArray> {
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
                    // 太老，丢弃
                    iterator.remove()
                }
                // frame.enqueueTimeMs > upperBound → 留着给后面的帧用
            }
        }
        return result
    }

    fun clear() { queue.clear() }
}