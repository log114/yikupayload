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
 * Modbus CRC16 计算与验证工具
 * 标准：多项式 0x8005，初始值 0xFFFF，输入输出反转，结果异或 0x0000
 * 输出：低字节在前 (Little-endian)
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
            // 将每个字节与CRC寄存器的低8位进行异或（由于Refin=True，数据需按位反转，但通过使用0xA001并右移已隐含此操作）
            crc = crc xor (byte.toInt() and 0xFF)
            // 处理每个字节的8个位
            for (j in 0..7) {
                // 检查最低位 (LSB)
                if (crc and 0x0001 != 0) {
                    crc = crc shr 1  // 右移一位
                    crc = crc xor POLYNOMIAL
                } else {
                    crc = crc shr 1
                }
            }
        }
        // 注意：计算结果是完整的16位CRC值，但按照Modbus RTU协议，传输时需要低字节在前。
        return crc.toUShort()
    }

    /**
     * 将CRC16值转换为低字节在前的ByteArray（长度为2）
     * 这是Modbus RTU帧的附加格式。
     * @param crc 计算出的CRC16值
     * @return 长度为2的ByteArray，index0为低字节，index1为高字节
     */
    fun crcToBytes(crc: UShort): ByteArray {
        val lowByte = (crc.toInt() and 0xFF).toByte()   // 低8位
        val highByte = ((crc.toInt() ushr 8) and 0xFF).toByte() // 高8位
        // Modbus RTU 要求低字节在前传输
        return byteArrayOf(lowByte, highByte)
    }

    /**
     * 验证接收到的完整数据帧（数据+CRC）的CRC是否正确
     * @param frame 完整的帧，包括最后的2字节CRC
     * @return true如果CRC校验通过
     */
    fun verifyFrame(frame: ByteArray): Boolean {
        if (frame.size < 3) {
            return false // 帧长度不足以包含地址/功能码和CRC
        }
        // 提取数据部分（除去最后2字节CRC）
        val dataLength = frame.size - 2
        val dataPart = frame.copyOfRange(0, dataLength)
        // 计算数据部分的CRC
        val calculatedCRC = calculateCRC(dataPart)
        // 从帧中提取附加的CRC（低字节在前）
        val receivedCRCLow = frame[dataLength].toInt() and 0xFF
        val receivedCRCHigh = frame[dataLength + 1].toInt() and 0xFF
        val receivedCRC = (receivedCRCHigh shl 8) or receivedCRCLow // 重组为UShort

        return (calculatedCRC.toInt() == receivedCRC)
    }
}