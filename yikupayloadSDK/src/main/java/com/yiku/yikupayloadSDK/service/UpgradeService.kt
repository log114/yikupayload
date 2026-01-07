package com.yiku.yikupayloadSDK.service

import android.R
import android.util.Log
import com.yiku.yikupayloadSDK.protocol.UPGRADE_PACKAGE_VERIFICATION
import com.yiku.yikupayloadSDK.protocol.UPGRADE_READ_DEVICE_INFO
import com.yiku.yikupayloadSDK.protocol.UPGRADE_RESET_DEVICE_INFO
import com.yiku.yikupayloadSDK.protocol.UPGRADE_RESTART_DEVICE
import com.yiku.yikupayloadSDK.protocol.UPGRADE_START
import com.yiku.yikupayloadSDK.protocol.UPGRADE_TRANSMISSION_PACKAGE
import com.yiku.yikupayloadSDK.util.MsgCallback
import com.yiku.yikupayloadSDK.util.UpgradeMsg
import com.yiku.yikupayloadSDK.util.bytesToHex
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.ArrayList
import java.util.Date
import kotlin.collections.plus
import kotlin.concurrent.thread

class UpgradeService {
    private val TAG = "upgradeService"

    var msgCallbacks: List<MsgCallback> = ArrayList()

    private var globalTid = 0L

    private var client: Socket? = null
    private var out: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false

    fun getIsConnected(): Boolean {
        return isConnected && client?.isConnected == true
    }

    fun registMsgCallback(msgCallback: MsgCallback) {
        this.msgCallbacks += msgCallback
    }

    fun connect(ip: String, port: Int): Boolean {
        //开启一个链接，需要指定地址和端口
        return try {
            if(ip == ""){
                return false
            }
            client = Socket(ip, port)
            out = client?.getOutputStream()
            Log.i(TAG, "设备连接成功")
            isConnected = true
            inputStream = client?.getInputStream()
            globalTid = Date().time
            thread {
                try {
                    val vTid = globalTid

                    Log.i(TAG, "recv start...")
                    isConnected = true
                    while (client?.isConnected == true) {
                        if (vTid != globalTid) {
                            break
                        }
                        val recv = ByteArray(1024)
                        inputStream?.read(recv)
                        if (recv.isEmpty()) {
                            continue
                        }
//                    Log.i(TAG, "recv:${String(recv)}")
                        for (msgCallback in msgCallbacks) {
                            msgCallback.onMsg(recv)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
            true
        } catch (e: Exception) {
            isConnected = false
            false
        }
    }
    // 断连
    fun disConnect() {
        if(getIsConnected()) {
            isConnected = false
            if (out != null) {
                out?.close()
            }
            if (inputStream != null) {
                inputStream?.close()
            }
            if (client != null && client?.isConnected == true) {
                client?.close()
            }
        }
    }

    fun sendData2Payload(data: ByteArray): Int {
        thread {
            try {
                Log.i(TAG, "sendData:${bytesToHex(data)}")
                //向输出流中写入数据，传向服务端
                if (!getIsConnected()) {
                    return@thread
                }
                if (out == null) {
                    Log.i(TAG, "out is null")
                    return@thread
                }
                out?.write(data)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                Log.e(TAG, "发送消息失败...")
//                sendData2Payload(data)
            }
        }
        return 0
    }

    // 获取设备信息
    fun getDeviceInfo() {
        val msg = UpgradeMsg()
        msg.msgId = UPGRADE_READ_DEVICE_INFO.toByte()
        msg.data = ByteArray(0)
        sendData2Payload(msg.getMsg())
    }

    // 重置远程升级信息
    fun resetDeviceInfo() {
        val msg = UpgradeMsg()
        msg.msgId = UPGRADE_RESET_DEVICE_INFO.toByte()
        msg.data = ByteArray(0)
        sendData2Payload(msg.getMsg())
    }

    // 下发远程升级请求
    fun startUpgrade(version: String, size: Int) {
        val msg = UpgradeMsg()
        msg.msgId = UPGRADE_START.toByte()
        msg.data = ByteArray(0)
        msg.data += versionStringToByteArray(version)
        msg.data += intToByteArrayLittleEndian(size)
        sendData2Payload(msg.getMsg())
    }

    // 下发升级数据包
    fun transmissionPackage(totalPackageNum: Int, packageIndex: Int, packageData: ByteArray) {
        val msg = UpgradeMsg()
        msg.msgId = UPGRADE_TRANSMISSION_PACKAGE.toByte()
        msg.data = ByteArray(0)
        msg.data += intToLittleEndianByteArray(totalPackageNum)
        msg.data += intToLittleEndianByteArray(packageIndex)
        msg.data += intToLittleEndianByteArray(packageData.size)
        msg.data += packageData
        sendData2Payload(msg.getMsg())
    }

    // 升级包校验
    fun packageVerification() {
        val msg = UpgradeMsg()
        msg.msgId = UPGRADE_PACKAGE_VERIFICATION.toByte()
        msg.data = ByteArray(0)
        sendData2Payload(msg.getMsg())
    }

    // 重启设备
    fun restartDevice() {
        val msg = UpgradeMsg()
        msg.msgId = UPGRADE_RESTART_DEVICE.toByte()
        msg.data = ByteArray(0)
        sendData2Payload(msg.getMsg())
    }

    fun versionStringToByteArray(version: String): ByteArray {
        val parts = version.split(".")
        require(parts.size == 4) { "版本号格式必须为 X.X.X.X" }

        return ByteArray(4) { index ->
            val part = parts.getOrNull(index)?.toIntOrNull()
            require(part != null && part in 0..255) {
                "版本号各部分必须在 0-255 范围内: ${parts[index]}"
            }
            part.toByte()
        }
    }

    /**
     * 将Int转换为4字节的ByteArray（小端序）
     * @param value 要转换的整数值
     * @return 4字节的小端序ByteArray
     */
    fun intToByteArrayLittleEndian(value: Int): ByteArray {
        return ByteArray(4) { index ->
            // 小端序：低字节在前
            (value shr (8 * index) and 0xFF).toByte()
        }
    }

    /**
     * 将ByteArray（小端序）转换回Int
     * @param bytes 4字节的小端序ByteArray
     * @return 转换后的整数值
     */
    fun byteArrayToIntLittleEndian(bytes: ByteArray): Int {
        require(bytes.size == 4) { "ByteArray长度必须为4" }

        var result = 0
        for (i in bytes.indices) {
            result = result or ((bytes[i].toInt() and 0xFF) shl (8 * i))
        }
        return result
    }

    /**
     * 小端序ByteArray(2) → Int
     */
    fun littleEndianToInt(byteArray: ByteArray, startIndex: Int = 0): Int {
        require(byteArray.size >= startIndex + 2) {
            "字节数组从索引${startIndex}开始长度不足2字节"
        }

        val lowByte = byteArray[startIndex].toInt() and 0xFF
        val highByte = byteArray[startIndex + 1].toInt() and 0xFF

        return (highByte shl 8) or lowByte
    }
    /**
     * Int → 小端序ByteArray(2)
     */
    fun intToLittleEndianByteArray(value: Int): ByteArray {
        require(value >= -32768 && value <= 65535) {
            "数值超出2字节表示范围: $value"
        }

        return byteArrayOf(
            (value and 0xFF).toByte(),        // 低字节
            ((value ushr 8) and 0xFF).toByte()  // 高字节
        )
    }
}