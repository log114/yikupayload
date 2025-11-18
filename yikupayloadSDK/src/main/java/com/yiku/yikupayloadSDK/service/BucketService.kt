package com.yiku.yikupayloadSDK.service

import android.util.Log
import com.yiku.yikupayloadSDK.protocol.BUCKET_BARREL_CONTROL
import com.yiku.yikupayloadSDK.protocol.BUCKET_BARREL_SAFETY_SWITCH
import com.yiku.yikupayloadSDK.protocol.BUCKET_HOOK_CONTROL
import com.yiku.yikupayloadSDK.util.BucketHost
import com.yiku.yikupayloadSDK.util.Msg
import com.yiku.yikupayloadSDK.util.MsgCallback
import com.yiku.yikupayloadSDK.util.bytesToHex
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.ArrayList
import kotlin.concurrent.thread

class BucketService {
    private val TAG = "BucketService"
    var msgCallbacks: List<MsgCallback> = ArrayList()

    private val port = 8519
    private lateinit var client: Socket
    private var out: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false
    private var host = ""

    open fun setIp(ip: String) {
        host = ip
    }
    open fun getIp(): String {
        return host
    }

    open fun getIsConnected(): Boolean {
        return isConnected && client.isConnected
    }
    open fun disConnect() {
        if(getIsConnected()) {
            isConnected = false
            client.close()
        }
    }

    open fun registMsgCallback(msgCallback: MsgCallback) {
        this.msgCallbacks += msgCallback
    }

    open fun connect(): Boolean {
        if(host == ""){
            host = BucketHost
        }
        //开启一个链接，需要指定地址和端口
        return try {
            Log.i(TAG, "吊桶连接：$host")
            client = Socket(host, port)
            out = client.getOutputStream()
            Log.i(TAG, "吊桶连接成功")
            isConnected = true
            inputStream = client.getInputStream()
            thread {
                Log.i(TAG, "recv start...")
                try {
                    while (client.isConnected) {
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
                    Log.i(TAG, "吊桶信息获取失败：$e")
                    e.printStackTrace()
                }
            }
            true
        } catch (e: Exception) {
            isConnected = false
//            Log.e(TAG, "connect error:${e.message}")
//            e.printStackTrace()
//            showToast("连接失败")
            false
        }
    }


    open fun sendData2Payload(data: ByteArray) {
        thread {
            try {
                Log.i(TAG, "吊桶，sendData:${bytesToHex(data)}")
                //向输出流中写入数据，传向服务端
                if (!getIsConnected()) {
                    return@thread
                }
//                Log.i(TAG, "sendData:${data.asList()}")
                Log.i(TAG, "sendData:${bytesToHex(data)}")
                out?.write(data)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "吊桶消息发送异常：$e")
//                sendData2Payload(data)
                isConnected = false
                client.close()
            }
        }
    }

    // 操作吊桶安全开关，0关，1开
    fun safetySwitch(switch: Int) {
        val msg = Msg();
        msg.msgId = BUCKET_BARREL_SAFETY_SWITCH.toByte()
        msg.payload = ByteArray(4)
        msg.payload[0] = switch.toByte()
        sendData2Payload(msg.getMsg())
    }

    // 操作吊桶开关，0停，1开（升），2关（降）
    fun barrelControl(controlType: Int) {
        val msg = Msg();
        msg.msgId = BUCKET_BARREL_CONTROL.toByte()
        msg.payload = ByteArray(4)
        msg.payload[0] = controlType.toByte()
        sendData2Payload(msg.getMsg())
    }

    // 操作挂钩开关，0关，1开
    fun hookControl(controlType: Int) {
        val msg = Msg();
        msg.msgId = BUCKET_HOOK_CONTROL.toByte()
        msg.payload = ByteArray(4)
        msg.payload[0] = controlType.toByte()
        sendData2Payload(msg.getMsg())
    }
}