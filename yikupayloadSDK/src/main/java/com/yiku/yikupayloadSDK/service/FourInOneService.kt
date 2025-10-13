package com.yiku.yikupayloadSDK.service

import android.os.Build
import android.util.Log
import com.yiku.yikupayloadSDK.protocol.FETCH_TEMPERATURE
import com.yiku.yikupayloadSDK.protocol.LUMINANCE_CHANGE
import com.yiku.yikupayloadSDK.protocol.OPEN_CLOSE_LIGHT
import com.yiku.yikupayloadSDK.protocol.RED_BLUE_FLASHES
import com.yiku.yikupayloadSDK.protocol.SEROV_CONTROL
import com.yiku.yikupayloadSDK.protocol.SHARP_FLASH
import com.yiku.yikupayloadSDK.util.Msg
import com.yiku.yikupayloadSDK.util.VehiclePlatform
import com.yiku.yikupayloadSDK.util.YA3Host
import com.yiku.yikupayloadSDK.util.bytesToHex
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import kotlin.concurrent.thread

class FourInOneService : BaseMegaphoneService() {
    private val TAG = "FourInOneService";

    private val port = 8519

    private var client: Socket? = null
    private var out: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false
    private var host = ""
    private val buffer = ByteArrayOutputStream()

    override fun setIp(ip: String) {
        host = ip
        setHost(host)
    }
    override fun getIp(): String {
        return host
    }
    init {
        platform = VehiclePlatform.H16
        if(host == ""){
            host = YA3Host
        }
        setHost(host)
    }

    override fun connect(): Boolean {
        //开启一个链接，需要指定地址和端口
        return try {
            client = Socket(host, port)
            out = client!!.getOutputStream()
            Log.i(TAG, "四合一连接成功")
            isConnected = true
            thread {
                try {
//                    val inputStream = client!!.getInputStream()
//                    val buffer = ByteArrayOutputStream()
//                    val recv = ByteArray(1024)
//
//                    while (true) {
//                        val bytesRead = inputStream.read(recv)
//                        if (bytesRead == -1) break
//
//                        buffer.write(recv, 0, bytesRead)
//                        val data = buffer.toByteArray()
//                        var startIndex = 0
//
//                        // 手动实现包头 [40] 查找
//                        while (startIndex <= data.size - 4) {
//                            if (data[startIndex] == '['.code.toByte() &&
//                                data[startIndex + 1] == '4'.code.toByte() &&
//                                data[startIndex + 2] == '0'.code.toByte() &&
//                                data[startIndex + 3] == ']'.code.toByte()
//                            ) {
//                                // 修复点：自定义查找结束符 ']'
//                                var endIndex = -1
//                                for (i in startIndex + 4 until data.size) {
//                                    if (data[i] == '['.code.toByte()) {
//                                        endIndex = i
//                                        break
//                                    }
//                                }
//
//                                if (endIndex != -1) { // 说明找到了下一个包的包头，有拼包
//                                    val packet = data.copyOfRange(startIndex, endIndex)
//                                    msgCallbacks.forEach { it.onMsg(packet) }
//                                    startIndex = endIndex
//                                } else { // 说明没找到下一个包的包头，无拼包
//                                    // 直接将剩余的所有数据写入进去
//                                    val packet = data.copyOfRange(startIndex, data.size)
//                                    msgCallbacks.forEach { it.onMsg(packet) }
//                                    break; // 已经处理完成，跳出循环
//                                }
//                            } else {
//                                startIndex++
//                            }
//                        }
//                        buffer.reset()
//                    }
                    while (client!!.isConnected) {
                        val recv = ByteArray(1024)
                        val i = inputStream?.read(recv)
                        if (i == 0) {
                            continue
                        }
//                        Log.i(TAG, "recv:${String(recv)}")
                        val data = recv.slice(0 until i!!).toByteArray()
                        var tmp = ByteArray(0);

                        data.forEach {
                            if(it.toInt().toChar() == '[') {
                                if (tmp.isNotEmpty() && tmp.size >= 4) {
                                    for (msgCallback in msgCallbacks) {
                                        msgCallback.onMsg(tmp)
                                    }
                                }
                                tmp = ByteArray(0);
                            }
                            tmp += it
                        }
                        if (tmp.isNotEmpty() && tmp.size >= 4) {
                            for (msgCallback in msgCallbacks) {
                                msgCallback.onMsg(tmp)
                            }
                            tmp = ByteArray(0);
                        }
                    }
                } catch (e: Exception) {
                    isConnected = false
                    e.printStackTrace()
                }
            }
            true
        } catch (e: Exception) {
            isConnected = false
            false
        }
    }

    override fun sendData2Payload(data: ByteArray): Int {
        thread {
            try {
                Log.i(TAG, "四合一，sendData:${bytesToHex(data)}")
                //向输出流中写入数据，传向服务端
                if (client == null || !client!!.isConnected || !isConnected) {
                    connect()
                }
                out?.write(data)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                Log.e(TAG, "传输失败，重试中...")
                isConnected = false
                sendData2Payload(data)
            }
        }
        return 0

    }

    /**
     * 开关灯控制/查询
     * open 开关状态 0关 1开
     * query 是否查询状态
     */
    override fun openLight(open: Int, query: Boolean) {

        val msg = Msg()
        msg.msgId = OPEN_CLOSE_LIGHT
        if (!query) {
            msg.payload = byteArrayOf(open.toByte())
        } else {
            msg.payload = byteArrayOf()
        }
        sendData2Payload(msg.getMsg())
    }

    /**
     * 亮度调整
     *
     * lum 亮度值 0-100
     * query 是否为查询
     */
    override fun luminanceChange(lum: Int, query: Boolean) {
        val msg = Msg()
        msg.msgId = LUMINANCE_CHANGE
        if (!query) {
            msg.payload = byteArrayOf(lum.toByte())
        } else {
            msg.payload = byteArrayOf()
        }
        sendData2Payload(msg.getMsg())
    }

    /**
     * 爆闪
     * open 1 开 0 关
     */
    override fun sharpFlash(open: Int, query: Boolean) {
        val msg = Msg()
        msg.msgId = SHARP_FLASH
        if (!query) {
            msg.payload = byteArrayOf(open.toByte())
        } else {
            msg.payload = byteArrayOf()
        }
        sendData2Payload(msg.getMsg())
    }

    /**
     * 获取温度
     *
     */
    override fun fetchTemperature() {
        val msg = Msg()
        msg.msgId = FETCH_TEMPERATURE
        msg.payload = byteArrayOf()
        sendData2Payload(msg.getMsg())
    }

    override fun redBlueLedControl(model: Byte) {
        val msg = Msg()
        msg.msgId = RED_BLUE_FLASHES
        msg.payload = byteArrayOf(model)
        sendData2Payload(msg.getMsg())
    }

    /**
     * 探照灯舵机控制 舵机值范围100-200
     */
    override fun controlServo(cval: Int) {
        val msg = Msg();
        // 控制舵机
        msg.msgId = SEROV_CONTROL.toByte()
        msg.payload = ByteArray(2)

        msg.payload[0] = 0xFF.toByte()
        msg.payload[1] = cval.toByte()
        sendData2Payload(msg.getMsg())
    }

    override fun getIsConnectedYA3(): Boolean {
        return if (client == null) {
            false
        } else (client!!.isConnected && isConnected)
    }
}