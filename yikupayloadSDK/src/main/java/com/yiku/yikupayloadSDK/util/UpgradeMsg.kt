package com.yiku.yikupayloadSDK.util

import java.nio.ByteBuffer

class UpgradeMsg(
    private val header: ByteArray = byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0X55, 0xFF.toByte()),
    private var len: ByteArray = ByteArray(2),
    private val address: ByteArray = byteArrayOf(0x00, 0x00, 0X00, 0x01),
    private val commandType: Byte = 0xE0.toByte(),
    var msgId: Byte = 0x00.toByte(),
    var data: ByteArray = ByteArray(0),
    private var checksum: ByteArray = ByteArray(2)
)  {
    fun getMsg(): ByteArray {
        val length = address.size + 1 + data.size
        len = byteArrayOf(
            (length and 0xFF).toByte(),
            ((length shr 8) and 0xFF).toByte()
        )
        var checksumData = ByteArray(0)
        checksumData += address
        checksumData += commandType
        checksumData += msgId
        if (data.isNotEmpty()) {
            checksumData += data
        }
        checksum = ModbusCRC16.crcToBytes(ModbusCRC16.calculateCRC(checksumData))
        var data = ByteArray(0)
        data += header
        data += len
        data += address
        data += commandType
        data += msgId
        data += data
        data += checksum
        return data
    }
}

object ModbusCRC16 {
    // 多项式: x16+x15+x2+1 (0x8005)
    private const val POLYNOMIAL = 0x8005
    // 初始值: 0xFFFF
    private const val INITIAL_VALUE = 0xFFFF
    // 结果异或: 0x0000
    private const val XOR_OUT = 0x0000
    // 预计算的CRC表（加速计算）
    private val crcTable = IntArray(256)

    init {
        // 初始化CRC表
        for (i in 0 until 256) {
            var crc = 0
            var c = i shl 8

            for (j in 0 until 8) {
                crc = crc shl 1
                if ((crc and 0x10000) != 0) {
                    crc = crc xor POLYNOMIAL
                }
                c = c shl 1
            }
            crcTable[i] = crc and 0xFFFF
        }
    }

    /**
     * 计算CRC16/MODBUS校验值
     * @param data 要计算CRC的数据（从地址的第一个字节开始到数据段的最后一个字节）
     * @return CRC16校验值，高字节在前格式
     */
    fun calculateCRC(data: ByteArray): UShort {
        var crc = INITIAL_VALUE
        for (byte in data) {
            // 输入反转：反转每个字节的位顺序
            val reversedByte = reverseBits(byte.toUByte())
            // 查表法计算CRC
            val index = ((crc shr 8) xor reversedByte.toInt()) and 0xFF
            crc = (crc shl 8) xor crcTable[index]
            crc = crc and 0xFFFF
        }
        // 输出反转：反转整个16位CRC值的位顺序
        var result = reverse16Bits(crc.toUShort()).toInt()
        // 结果异或
        result = result xor XOR_OUT
        return result.toUShort()
    }

    /**
     * 反转8位的位顺序
     */
    private fun reverseBits(byte: UByte): UByte {
        var b = byte.toInt()
        var reversed = 0
        for (i in 0 until 8) {
            reversed = (reversed shl 1) or (b and 0x01)
            b = b shr 1
        }
        return reversed.toUByte()
    }

    /**
     * 反转16位的位顺序
     */
    private fun reverse16Bits(value: UShort): UShort {
        var v = value.toInt()
        var reversed = 0
        for (i in 0 until 16) {
            reversed = (reversed shl 1) or (v and 0x0001)
            v = v shr 1
        }
        return reversed.toUShort()
    }

    /**
     * 将CRC值转换为高字节在前的字节数组
     */
    fun crcToBytes(crc: UShort): ByteArray {
        val bytes = ByteBuffer.allocate(2)
        // 高字节在前
        bytes.putShort(0, crc.toShort())
        return bytes.array()
    }

    /**
     * 验证数据包的CRC校验
     * @param packet 完整的帧数据（包括帧头）
     * @param dataStart 数据起始位置（地址的第一个字节）
     * @param dataEnd 数据结束位置（数据段的最后一个字节）
     * @param crcPosition CRC在包中的位置
     */
    fun verifyPacket(
        packet: ByteArray,
        dataStart: Int,
        dataEnd: Int,
        crcPosition: Int
    ): Boolean {
        // 提取要计算CRC的数据部分
        val dataToCheck = packet.copyOfRange(dataStart, dataEnd + 1)
        // 计算CRC
        val calculatedCRC = calculateCRC(dataToCheck)
        // 获取包中的CRC值
        val packetCRC = ((packet[crcPosition].toInt() and 0xFF) shl 8) or
                (packet[crcPosition + 1].toInt() and 0xFF)
        return (calculatedCRC.toInt() == packetCRC)
    }
}