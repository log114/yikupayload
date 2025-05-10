package com.yiku.yikupayloadSDK.service

import android.util.Log
import com.yiku.yikupayloadSDK.protocol.WATERBRANCH_HOSE_DETACHMENT
import com.yiku.yikupayloadSDK.protocol.WATERBRANCH_HOSE_RELEASE
import com.yiku.yikupayloadSDK.protocol.WATERBRANCH_OPERATE
import com.yiku.yikupayloadSDK.protocol.WATERBRANCH_STATE_SEND
import com.yiku.yikupayloadSDK.util.Msg
import com.yiku.yikupayloadSDK.util.MsgCallback
import com.yiku.yikupayloadSDK.util.WaterBranchHost
import com.yiku.yikupayloadSDK.util.bytesToHex
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.ArrayList
import kotlin.concurrent.thread

class WaterBranchService {
    private val TAG = "WaterBranchService"
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
            host = WaterBranchHost
        }
        //开启一个链接，需要指定地址和端口
        return try {
            Log.i(TAG, "消防水枪连接：$host")
            client = Socket(host, port)
            out = client.getOutputStream()
            Log.i(TAG, "消防水枪连接成功")
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
                    Log.i(TAG, "消防水枪信息获取失败：$e")
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
                Log.i(TAG, "水枪，sendData:${bytesToHex(data)}")
                //向输出流中写入数据，传向服务端
                if (!getIsConnected()) {
                    return@thread
                }
//                Log.i(TAG, "sendData:${data.asList()}")
                Log.i(TAG, "sendData:${bytesToHex(data)}")
                out?.write(data)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "水枪消息发送异常：$e")
//                sendData2Payload(data)
                isConnected = false
                client.close()
            }
        }
    }

    // 操作水枪开关，0关，1开
    fun operate(operateType: Int) {
        val msg = Msg();
        msg.msgId = WATERBRANCH_OPERATE.toByte()
        msg.payload = ByteArray(4)
        msg.payload[0] = operateType.toByte()
        sendData2Payload(msg.getMsg())
    }

    // 释放水带
    fun hoseRelease() {
        val msg = Msg();
        msg.msgId = WATERBRANCH_HOSE_RELEASE.toByte()
        msg.payload = ByteArray(4)
        sendData2Payload(msg.getMsg())
    }

    // 水带脱困
    fun hoseDetachment() {
        val msg = Msg();
        msg.msgId = WATERBRANCH_HOSE_DETACHMENT.toByte()
        msg.payload = ByteArray(4)
        sendData2Payload(msg.getMsg())
    }

    // 发送心跳包
    fun heartbeat() {
        val msg = Msg();
        msg.msgId = WATERBRANCH_STATE_SEND.toByte()
        msg.payload = ByteArray(4)
        sendData2Payload(msg.getMsg())
    }
}