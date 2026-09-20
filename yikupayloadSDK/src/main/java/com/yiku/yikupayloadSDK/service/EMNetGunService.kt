package com.yiku.yikupayloadSDK.service

import android.util.Log
import com.yiku.yikupayloadSDK.protocol.EMITTER_LUNCH
import com.yiku.yikupayloadSDK.protocol.EMITTER_STATUS
import com.yiku.yikupayloadSDK.protocol.EMNETGUN_HEARTBEAT
import com.yiku.yikupayloadSDK.protocol.EMNETGUN_LASER
import com.yiku.yikupayloadSDK.protocol.EMNETGUN_LAUNCH
import com.yiku.yikupayloadSDK.protocol.EMNETGUN_PITCH
import com.yiku.yikupayloadSDK.util.EMNetGunHost
import com.yiku.yikupayloadSDK.util.EmitterHost
import com.yiku.yikupayloadSDK.util.Msg
import com.yiku.yikupayloadSDK.util.MsgCallback
import com.yiku.yikupayloadSDK.util.bytesToHex
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.ArrayList
import kotlin.concurrent.thread

open class EMNetGunService {
    private val TAG = "EMNetGunService"
    var msgCallbacks: List<MsgCallback> = ArrayList()

    private var port = 8519
    private lateinit var client: Socket
    private var out: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false
    private var host = ""

    open fun getIsConnected(): Boolean {
        return isConnected && client.isConnected
    }

    open fun registMsgCallback(msgCallback: MsgCallback) {
        this.msgCallbacks += msgCallback
    }
    open fun disConnect() {
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

    open fun setIp(ip: String) {
        host = ip
    }
    open fun getIp(): String {
        return host
    }
    open fun setPort(newPort: Int) {
        port = newPort
    }

    open fun connect(): Boolean {
        if(host == ""){
            host = EMNetGunHost
        }
        //开启一个链接，需要指定地址和端口
        return try {
            client = Socket(host, port)
            out = client.getOutputStream()
            Log.i(TAG, "电磁网枪连接成功")
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
            Log.e(TAG, "电磁网枪连接失败，ip:${host}，error：${e.message}")
//            e.printStackTrace()
//            showToast("连接失败")
            false
        }
    }


    open fun sendData2Payload(data: ByteArray): Int {
        thread {
            try {
                Log.i(TAG, "电磁网枪，sendData:${bytesToHex(data)}")
                //向输出流中写入数据，传向服务端
                if (!getIsConnected()) {
                    return@thread
                }
                out?.write(data)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                Log.e(TAG, "电磁网枪消息发送异常：$e")
                isConnected = false
                client.close()
            }
        }
        return 0
    }

    fun heartbeat() {
        val msg = Msg()
        msg.msgId = EMNETGUN_HEARTBEAT.toByte()
        msg.payload = ByteArray(0)
        sendData2Payload(msg.getMsg())
    }

    fun launch() {
        val msg = Msg()
        msg.msgId = EMNETGUN_LAUNCH.toByte()
        msg.payload = ByteArray(1)
        sendData2Payload(msg.getMsg())
    }

    fun setPitch(pitch: Int) {
        val msg = Msg()
        msg.msgId = EMNETGUN_PITCH.toByte()
        msg.payload = ByteArray(1)
        msg.payload[0] = pitch.toByte()
        sendData2Payload(msg.getMsg())
    }

    fun laserSwitch(isOpen: Boolean) {
        val msg = Msg()
        msg.msgId = EMNETGUN_LASER.toByte()
        msg.payload = ByteArray(1)
        msg.payload[0] = if(isOpen) 0x01 else 0x00
        sendData2Payload(msg.getMsg())
    }
}