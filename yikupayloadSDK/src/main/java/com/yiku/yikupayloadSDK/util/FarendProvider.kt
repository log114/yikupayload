package com.yiku.yikupayloadSDK.util

class FarendProvider(
    private val frameSize8k: Int = 80,
    private val maxSize: Int = 200
) {
    data class TimedFrame(val pcm: ShortArray, val enqueueTimeMs: Long)

    private val queue = java.util.concurrent.ConcurrentLinkedDeque<TimedFrame>()

    fun feed(pcm: ShortArray, is16k: Boolean) {
        val pcm8k = if (is16k) Uilts.downsample16kTo8k(pcm) else pcm
        var i = 0
        while (i < pcm8k.size) {
            val end = minOf(i + frameSize8k, pcm8k.size)
            val frame = ShortArray(frameSize8k)
            pcm8k.copyInto(frame, 0, i, end)
            if (queue.size >= maxSize) queue.pollFirst()
            queue.offerLast(TimedFrame(frame, System.currentTimeMillis()))
            i += frameSize8k
        }
    }

    fun onBeforePlay(pcm16k: ShortArray) = feed(pcm16k, true)
    fun onBeforeSend(pcm8k: ShortArray) = feed(pcm8k, false)

    /** 返回匹配的完整 TimedFrame（调用侧自己取 .pcm / .enqueueTimeMs） */
    fun pollAlignedFrames(targetTimeMs: Long, windowMs: Long = 40): List<TimedFrame> {
        val lower = targetTimeMs - windowMs
        val upper = targetTimeMs + windowMs
        val result = mutableListOf<TimedFrame>()
        val iter = queue.iterator()
        while (iter.hasNext()) {
            val f = iter.next()
            when {
                f.enqueueTimeMs in lower..upper -> {
                    result.add(f)
                    iter.remove()
                }
                f.enqueueTimeMs < lower -> iter.remove()
            }
        }
        return result
    }

    fun clear() = queue.clear()
}