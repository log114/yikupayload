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
    /** 录音端调用 */
    fun onBeforeRecord(pcm8k: ShortArray) {
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
    fun pollAlignedFrames(echoOriginTimeMs: Long, windowMs: Long = 35): List<ShortArray> {
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

    /**
     * 严格返回 6 帧（60 ms），对应近端当前块
     * @param targetMs 第 0 帧的期望入队时间
     */
    fun pollExact6(targetMs: Long, windowMs: Long = 35): List<ShortArray> {
        val slots = arrayOfNulls<ShortArray>(6)
        val iter = queue.iterator()
        val lower = targetMs - windowMs
        val upper = targetMs + 60 + windowMs   // 覆盖 6 帧范围

        while (iter.hasNext()) {
            val f = iter.next()
            if (f.enqueueTimeMs < lower - 80) {
                // 太老，不可能再被任何槽用到
                iter.remove()
                continue
            }
            if (f.enqueueTimeMs > upper) {
                // 太新，留给下几轮
                break
            }
            // 算它该进哪个槽（四舍五入）
            val offset = f.enqueueTimeMs - targetMs
            val k = (offset / 10).toInt().coerceIn(0, 5)
            // 边界：如果偏移负太多（比如 -30ms），归到槽 0；正超了归槽 5
            if (slots[k] == null) {
                slots[k] = f.pcm
                iter.remove()   // ★ 用过就删，绝不二次喂
            } else {
                iter.remove()   // 同槽重复帧，扔
            }
        }
        // 补静音 + 转 List
        return slots.map { it ?: ShortArray(80) }
    }

    fun clear() { queue.clear() }
}