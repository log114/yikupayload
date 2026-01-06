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

/**
 * Modbus CRC16 计算工具（支持高字节在前输出）
 * 标准算法参数：多项式 0x8005（计算中使用其位反转形式 0xA001），初始值 0xFFFF
 * 输出格式：高字节在前 (Big-endian)
 */
object ModbusCRC16 {

    // 算法参数
    private const val POLYNOMIAL = 0xA001   // 0x8005 的位反转形式，便于右移计算
    private const val INITIAL_VALUE = 0xFFFF

    /**
     * 计算给定数据的 Modbus CRC16 校验值
     * @param data 要计算校验和的数据（不包括最后的2字节CRC字段）
     * @return CRC16 校验值
     */
    fun calculateCRC(data: ByteArray): UShort {
        var crc = INITIAL_VALUE

        for (byte in data) {
            crc = crc xor (byte.toInt() and 0xFF)
            for (j in 0..7) {
                if (crc and 0x0001 != 0) {
                    crc = crc shr 1
                    crc = crc xor POLYNOMIAL
                } else {
                    crc = crc shr 1
                }
            }
        }
        return crc.toUShort()
    }

    /**
     * 将CRC16值转换为高字节在前的ByteArray（长度为2）
     * 这是您要求的自定义格式。
     * @param crc 计算出的CRC16值
     * @return 长度为2的ByteArray，index0为高字节，index1为低字节
     */
    fun crcToBytes(crc: UShort): ByteArray {
        val highByte = ((crc.toInt() ushr 8) and 0xFF).toByte() // 高8位
        val lowByte = (crc.toInt() and 0xFF).toByte()           // 低8位
        // 您的要求：高字节在前
        return byteArrayOf(highByte, lowByte)
    }

    /**
     * 验证接收到的完整数据帧（数据+CRC）的CRC是否正确
     * 注意：此函数假设接收到的帧中CRC部分也是高字节在前
     * @param frame 完整的帧，包括最后的2字节CRC
     * @return true如果CRC校验通过
     */
    fun verifyFrame(frame: ByteArray): Boolean {
        if (frame.size < 3) {
            return false
        }
        val dataLength = frame.size - 2
        val dataPart = frame.copyOfRange(0, dataLength)

        val calculatedCRC = calculateCRC(dataPart)

        // 从帧中提取附加的CRC（高字节在前）
        val receivedCRCHigh = frame[dataLength].toInt() and 0xFF
        val receivedCRCLow = frame[dataLength + 1].toInt() and 0xFF
        val receivedCRC = (receivedCRCHigh shl 8) or receivedCRCLow

        return (calculatedCRC.toInt() == receivedCRC)
    }
}