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
        val length = address.size + 1 + 1 + data.size // 长度计算包括：地址+命令类型+指令码+具体数据
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
        var sendData = ByteArray(0)
        sendData += header
        sendData += len
        sendData += address
        sendData += commandType
        sendData += msgId
        sendData += data
        sendData += checksum
        return sendData
    }
}

object ModbusCRC16 {
    // 多项式: x^16 + x^15 + x^2 + 1 (0x8005)，但计算时使用其位反转形式0xA001
    private const val POLYNOMIAL = 0xA001  // 修正：使用位反转多项式

    // 初始值: 0xFFFF
    private const val INITIAL_VALUE = 0xFFFF

    // 预计算的CRC表（查表法加速）
    private val crcTable = IntArray(256)

    init {
        // 正确初始化CRC表[5](@ref)
        for (i in 0 until 256) {
            var crc = i
            for (j in 0 until 8) {
                if (crc and 0x0001 != 0) {
                    crc = (crc shr 1) xor POLYNOMIAL
                } else {
                    crc = crc shr 1
                }
            }
            crcTable[i] = crc
        }
    }

    /**
     * 计算CRC16/MODBUS校验值（标准实现）
     * @param data 要计算CRC的数据（从地址字段到数据字段）
     * @return CRC16校验值
     */
    fun calculateCRC(data: ByteArray): UShort {
        var crc = INITIAL_VALUE

        for (byte in data) {
            // 标准Modbus CRC计算，不需要字节位反转[1](@ref)
            val index = (crc xor (byte.toInt() and 0xFF)) and 0xFF
            crc = (crc ushr 8) xor crcTable[index]
        }

        return crc.toUShort()
    }

    /**
     * 替代方案：直接计算法（更容易验证）
     */
    fun calculateCRCDirect(data: ByteArray): UShort {
        var crc = INITIAL_VALUE

        for (byte in data) {
            crc = crc xor (byte.toInt() and 0xFF)
            for (j in 0 until 8) {
                if (crc and 0x0001 != 0) {
                    crc = (crc shr 1) xor POLYNOMIAL
                } else {
                    crc = crc shr 1
                }
            }
        }

        return crc.toUShort()
    }

    /**
     * 将CRC值转换为Modbus字节顺序（低字节在前）
     */
    fun crcToBytes(crc: UShort): ByteArray {
        return byteArrayOf(
            (crc.toInt() and 0xFF).toByte(),        // 低字节在前
            ((crc.toInt() shr 8) and 0xFF).toByte() // 高字节在后
        )
    }

    /**
     * 验证数据包的CRC校验
     */
    fun verifyPacket(
        packet: ByteArray,
        dataStart: Int,
        dataEnd: Int,
        crcPosition: Int
    ): Boolean {
        require(packet.size >= crcPosition + 2) { "数据包长度不足" }

        // 提取要计算CRC的数据部分
        val dataToCheck = packet.copyOfRange(dataStart, dataEnd + 1)

        // 计算CRC
        val calculatedCRC = calculateCRC(dataToCheck)

        // 提取包中的CRC值（低字节在前格式）
        val packetCRCLow = packet[crcPosition].toInt() and 0xFF
        val packetCRCHigh = packet[crcPosition + 1].toInt() and 0xFF
        val packetCRC = (packetCRCHigh shl 8) or packetCRCLow  // 修正字节顺序

        return calculatedCRC.toInt() == packetCRC
    }
}