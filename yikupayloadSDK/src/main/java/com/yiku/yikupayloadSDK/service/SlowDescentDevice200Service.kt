package com.yiku.yikupayloadSDK.service

import android.util.Log
import com.yiku.yikupayloadSDK.protocol.DESCENT200_CONTROL
import com.yiku.yikupayloadSDK.protocol.DESCENT200_EMERGENCY_CONTROL
import com.yiku.yikupayloadSDK.protocol.DESCENT200_HOOK_CONTROL
import com.yiku.yikupayloadSDK.protocol.DESCENT200_LENGTH_CONTROL
import com.yiku.yikupayloadSDK.protocol.DESCENT200_RESET_WEIGHT_CONTROL
import com.yiku.yikupayloadSDK.protocol.DESCENT200_SPEED_CONTROL
import com.yiku.yikupayloadSDK.protocol.DESCENT200_WARNING_LIGHT
import com.yiku.yikupayloadSDK.util.Msg
import com.yiku.yikupayloadSDK.util.MsgCallback
import com.yiku.yikupayloadSDK.util.SlowDescentDevice200Host
import com.yiku.yikupayloadSDK.util.bytesToHex
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.ArrayList
import kotlin.concurrent.thread

open class SlowDescentDevice200Service {
    private val TAG = "SlowDescentDevice200Service"
    var msgCallbacks: List<MsgCallback> = ArrayList()
    private val port = 8519
    private lateinit var client: Socket
    private var out: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false
    private var host = ""

    fun setIp(ip: String) {
        host = ip
    }
    fun getIp(): String {
        return host
    }

    fun getIsConnected(): Boolean {
        return isConnected
    }

    fun registMsgCallback(msgCallback: MsgCallback) {
        this.msgCallbacks += msgCallback
    }
    var parseIndex = 0;
    var recvData = ByteArray(128)
    var recvDataLast =  ByteArray(128)
    private fun parseByte(b: Byte): Boolean {
        when (parseIndex) {
            0 -> { // header
                if (b != 0x8d.toByte()) {
                    parseIndex = 0
                    recvData = ByteArray(128)
                    return false
                }
                recvData[0] = b
                parseIndex++
                return false

            }

            1 -> { //LEN
                recvData[1] = b
                parseIndex++
                return false

            }

            2 -> { // MSG_ID
                recvData[2] = b
                parseIndex++
                return false
            }

            else -> {
                recvData[parseIndex] = b
                parseIndex++
                return if (parseIndex >= recvData[1].toInt() + 4) {
                    parseIndex = 0
                    recvDataLast = recvData
                    recvData = ByteArray(128)
                    true
                } else {
                    false
                }
            }
        }
    }

    fun connect(): Boolean {
        Log.i(TAG, "SlowDescentDevice200Service   connect...")
        if(host == ""){
            host = SlowDescentDevice200Host
        }
        if (isConnected){
            return true
        }
        //开启一个链接，需要指定地址和端口
        return try {
            client = Socket(host, port)
            out = client.getOutputStream()
            Log.i(TAG, "200kg缓降器连接成功")
            isConnected = true
            inputStream = client.getInputStream()
            thread {
                Log.i(TAG, "recv start...")
                try {
                    while (client.isConnected) {
                        val recv = ByteArray(1024)
                        val i = inputStream?.read(recv)
                        if (i == 0) {
                            continue
                        }
                        val data = recv.slice(0 until i!!).toByteArray()
//                    Log.i(TAG, "recv:${String()}")
                        data.forEach {
                            run {
                                if (parseByte(it)) {
                                    for (msgCallback in msgCallbacks) {
                                        msgCallback.onMsg(recvDataLast)
                                    }
                                }

                            }
                        }
                    }
                }
                catch (e: Exception) {
                    Log.e(TAG, e.toString())
                }
            }
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
            client.close()
        }
    }

    fun sendData2Payload(data: ByteArray): Int {
        thread {
            try {
                Log.i(TAG, "200kg缓降器，sendData:${bytesToHex(data)}")
                //向输出流中写入数据，传向服务端
                if (!isConnected || !client.isConnected) {
                    connect()
                }
                out?.write(data)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                Log.e(TAG, "传输失败，重试中...")
                sendData2Payload(data)
            }
        }
        return 0
    }
    // 安全开关
    fun descentControl(flag: Boolean) {
        val msg = Msg()
        msg.msgId = DESCENT200_CONTROL.toByte()
        msg.payload = ByteArray(1)
        // Enable缓降器
        if(flag){
            msg.payload[0] = 0x01.toByte()
        }
        // Disable缓降器
        else {
            msg.payload[0] = 0x00.toByte()
        }
        sendData2Payload(msg.getMsg())
    }

    // 警示灯开关
    fun warningLightControl(flag: Boolean) {
        val msg = Msg()
        msg.msgId = DESCENT200_WARNING_LIGHT.toByte()
        msg.payload = ByteArray(1)
        // 开
        if(flag){
            msg.payload[0] = 0x01.toByte()
        }
        // 关
        else {
            msg.payload[0] = 0x00.toByte()
        }
        sendData2Payload(msg.getMsg())
    }

    // 缓降器紧急控制，0: 复位，1：急停，2：熔断
    fun emergencyControl(command: Int) {
        val msg = Msg()
        msg.msgId = DESCENT200_EMERGENCY_CONTROL.toByte()
        msg.payload = ByteArray(1)
        // 解除紧急状态（在发送了紧急停车或紧急熔断命令后，突然想取消停车和熔断的时候使用）
        when (command) {
            // 复位
            0 -> {
                msg.payload[0] = 0x00.toByte()
            }
            // 急停
            1 -> {
                msg.payload[0] = 0x01.toByte()
            }
            // 熔断
            2 -> {
                msg.payload[0] = 0x02.toByte()
            }
        }
        sendData2Payload(msg.getMsg())
    }

    /* 速度控制
    * speed: 速度（-100~100cm/s）
    * */
    fun controlBySpeed(speed: Int) {
        val msg = Msg()
        msg.msgId = DESCENT200_SPEED_CONTROL.toByte()
        msg.payload = ByteArray(1)
        msg.payload[0] = speed.toByte()
        sendData2Payload(msg.getMsg())
    }

    /* 放线长度控制
    * length: 放线长度（0~100m）
    * */
    fun controlByLength(length: Int) {
        val msg = Msg()
        msg.msgId = DESCENT200_LENGTH_CONTROL.toByte()
        msg.payload = ByteArray(1)
        msg.payload[0] = length.toByte()
        sendData2Payload(msg.getMsg())
    }

    // 挂钩开关
    fun hookControl(flag: Boolean) {
        val msg = Msg()
        msg.msgId = DESCENT200_HOOK_CONTROL.toByte()
        msg.payload = ByteArray(1)
        // 开
        if(flag){
            msg.payload[0] = 0x01.toByte()
        }
        // 关
        else {
            msg.payload[0] = 0x00.toByte()
        }
        sendData2Payload(msg.getMsg())
    }

    // 重量清零
    fun resetWeight() {
        val msg = Msg()
        msg.msgId = DESCENT200_RESET_WEIGHT_CONTROL.toByte()
        msg.payload = ByteArray(1)
        sendData2Payload(msg.getMsg())
    }
}