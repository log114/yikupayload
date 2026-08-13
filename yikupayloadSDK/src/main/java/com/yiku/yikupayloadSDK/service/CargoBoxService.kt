package com.yiku.yikupayloadSDK.service

import android.util.Log
import com.yiku.yikupayloadSDK.protocol.CARGOBOX_ACCELERATE
import com.yiku.yikupayloadSDK.protocol.CARGOBOX_DECELERATE
import com.yiku.yikupayloadSDK.protocol.CARGOBOX_ENABLE
import com.yiku.yikupayloadSDK.util.CargoBoxHost
import com.yiku.yikupayloadSDK.util.Msg
import com.yiku.yikupayloadSDK.util.MsgCallback
import com.yiku.yikupayloadSDK.util.ThrowerHost
import com.yiku.yikupayloadSDK.util.bytesToHex
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.ArrayList
import kotlin.concurrent.thread

open class CargoBoxService {
    private val TAG = "CargoBoxService"
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

    open fun connect(): Boolean {
        if (host == "") {
            host = CargoBoxHost
        }
        if (isConnected){
            return true
        }
        //开启一个链接，需要指定地址和端口
        return try {
            Log.i(TAG, "运输箱 连接：${host}:${port}")
            client = Socket(host, port)
            out = client.getOutputStream()
            Log.i(TAG, "运输箱 连接成功")
            isConnected = true
            inputStream = client.getInputStream()
            thread {
                Log.i(TAG, "recv start...")
                while (getIsConnected()) {
                    try {
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
                    } catch (e: Exception) {
                        Log.i(TAG, "运输箱 信息获取失败：$e")
                        e.printStackTrace()
                    }
                }
            }
            true
        } catch (e: Exception) {
            isConnected = false
            Log.i(TAG, "运输箱 连接失败，ip:${host}，端口：${port}，error:${e.message}")
            e.printStackTrace()
//            showToast("连接失败")
            false
        }
    }

    open fun sendData2Payload(data: ByteArray) {
        thread {
            try {
                Log.i(TAG, "运输箱，sendData:${bytesToHex(data)}")
                //向输出流中写入数据，传向服务端
                if (!getIsConnected()) {
                    return@thread
                }
//                Log.i(TAG, "sendData:${data.asList()}")
                out?.write(data)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "运输箱 消息发送异常：$e")
                isConnected = false
                client.close()
            }
        }
    }

    // 压缩机使能
    fun setEnable(isEnable: Boolean) {
        val msg = Msg();
        msg.msgId = CARGOBOX_ENABLE.toByte()
        msg.payload = ByteArray(1)
        msg.payload[0] = if(isEnable){1}else{0}
        sendData2Payload(msg.getMsg())
    }

    // 提高功率
    fun increasePower() {
        val msg = Msg();
        msg.msgId = CARGOBOX_ACCELERATE.toByte()
        msg.payload = ByteArray(1)
        sendData2Payload(msg.getMsg())
    }

    // 降低功率
    fun reducePower() {
        val msg = Msg();
        msg.msgId = CARGOBOX_DECELERATE.toByte()
        msg.payload = ByteArray(1)
        sendData2Payload(msg.getMsg())
    }
}