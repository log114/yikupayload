package com.yiku.yikupayloadSDK.util

import kotlin.experimental.xor

data class PL_Msg(
    private val header: ByteArray = byteArrayOf(0x55, 0xAA.toByte(), 0xDC.toByte()),

    private var len: Byte = 0.toByte(),

    var msgId: Byte = 0x00.toByte(),

    var payload: ByteArray = ByteArray(0),

    private var checksum: Byte = 0x00.toByte()
) {
    fun getMsg(): ByteArray {
        len = (payload.size + 3).toByte() // 长度包含len本身和校验码checksum
        var checksumData = ByteArray(0)
        checksumData += header
        checksumData += len
        checksumData += msgId
        if (payload.isNotEmpty()) {
            checksumData += payload
        }
        checksum = viewlinkProtocolChecksum(checksumData)
        var data = ByteArray(0)
        data += checksumData
        data += checksum
        return data
    }

    private fun viewlinkProtocolChecksum(viewlinkDataBuf: ByteArray): Byte {
        val len = viewlinkDataBuf[3]
        var checksum = len

        for (i in 0 until len.toInt() - 2) {
            checksum = checksum xor viewlinkDataBuf[4 + i]
        }

        return checksum
    }
}