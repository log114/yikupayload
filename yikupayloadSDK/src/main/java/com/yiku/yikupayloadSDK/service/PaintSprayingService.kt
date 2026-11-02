package com.yiku.yikupayloadSDK.service

import android.util.Log
import com.yiku.yikupayloadSDK.protocol.PAINTSPRAYING_CONTROL
import com.yiku.yikupayloadSDK.util.PaintSprayingHost
import com.yiku.yikupayloadSDK.util.Msg
import com.yiku.yikupayloadSDK.util.MsgCallback
import com.yiku.yikupayloadSDK.util.bytesToHex
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.ArrayList
import kotlin.concurrent.thread

class PaintSprayingService {
    private val TAG = "PaintSprayingService"
    var msgCallbacks: List<MsgCallback> = ArrayList()

    private var port = 8519
    private lateinit var client: Socket
    private var out: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false
    private var host = ""

    fun getIsConnected(): Boolean {
        return isConnected && client.isConnected
    }

    fun registMsgCallback(msgCallback: MsgCallback) {
        this.msgCallbacks += msgCallback
    }
    fun disConnect() {
        if(getIsConnected()) {
            isConnected = false
            client.close()
        }
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
                return if (parseIndex >= recvData[1].toInt() + 4) {
                    parseIndex = 0
                    recvDataLast = recvData
                    recvData = ByteArray(128)
                    true
                } else {
                    recvData[parseIndex] = b
                    parseIndex++
                    false
                }
            }

        }
    }

    fun setIp(ip: String) {
        host = ip
    }
    fun getIp(): String {
        return host
    }
    fun setPort(newPort: Int) {
        port = newPort
    }

    fun connect(): Boolean {
        if(host == ""){
            host = PaintSprayingHost
        }
        //开启一个链接，需要指定地址和端口
        return try {
            client = Socket(host, port)
            out = client.getOutputStream()
            Log.i(TAG, "油漆喷涂连接成功")
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
            Log.e(TAG, "油漆喷涂连接失败，ip:${host}，error：${e.message}")
//            e.printStackTrace()
//            showToast("连接失败")
            false
        }
    }


    fun sendData2Payload(data: ByteArray): Int {
        thread {
            try {
                Log.i(TAG, "油漆喷涂，sendData:${bytesToHex(data)}")
                //向输出流中写入数据，传向服务端
                if (!getIsConnected()) {
                    return@thread
                }
                out?.write(data)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                Log.e(TAG, "油漆喷涂消息发送异常：$e")
                isConnected = false
                client.close()
            }
        }
        return 0
    }

    fun gearControl(gear0: Int, gear1: Int) {
        val msg = Msg()
        msg.msgId = PAINTSPRAYING_CONTROL.toByte()
        msg.payload = ByteArray(2)
        msg.payload[0] = gear0.toByte()
        msg.payload[1] = gear1.toByte()
        sendData2Payload(msg.getMsg())
    }
}