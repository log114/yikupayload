package com.yiku.yikupayloadSDK.service

import android.util.Log
import com.yiku.yikupayloadSDK.protocol.SETIP_READ_IP
import com.yiku.yikupayloadSDK.protocol.SETIP_RESET
import com.yiku.yikupayloadSDK.protocol.SETIP_RESTART
import com.yiku.yikupayloadSDK.protocol.SETIP_SET_IP
import com.yiku.yikupayloadSDK.util.Msg
import com.yiku.yikupayloadSDK.util.MsgCallback
import com.yiku.yikupayloadSDK.util.bytesToHex
import com.yiku.yikupayloadSDK.util.int16ToByteArrayLE
import com.yiku.yikupayloadSDK.util.ipStringToByteArray
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.ArrayList
import java.util.Date
import kotlin.collections.plus
import kotlin.concurrent.thread

class SetDeviceIpService {
    private val TAG = "SetDeviceIpService"

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

    // 获取设备ip信息
    fun getDeviceIpInfo() {
        val msg = Msg()
        msg.msgId = SETIP_READ_IP.toByte()
        msg.payload = ByteArray(1)
        sendData2Payload(msg.getMsg())
    }

    // 设置ip配置
    fun setDeviceIpInfo(ip: String, gateway: String, port: Int) {
        val msg = Msg()
        msg.msgId = SETIP_SET_IP.toByte()
        msg.payload = ByteArray(10)
        val ipArray = ipStringToByteArray(ip)
        val gatewayArray = ipStringToByteArray(gateway)
        val portArray = int16ToByteArrayLE(port)
        System.arraycopy(ipArray, 0, msg.payload, 0, 4)
        System.arraycopy(gatewayArray, 0, msg.payload, 4, 4)
        System.arraycopy(portArray, 0, msg.payload, 8, 2)
        sendData2Payload(msg.getMsg())
    }

    // 恢复出厂设置
    fun resetIpInfo() {
        val msg = Msg()
        msg.msgId = SETIP_RESET.toByte()
        msg.payload = ByteArray(1)
        sendData2Payload(msg.getMsg())
    }

    // 重启设备
    fun restartDevice() {
        val msg = Msg()
        msg.msgId = SETIP_RESTART.toByte()
        msg.payload = ByteArray(1)
        sendData2Payload(msg.getMsg())
    }
}