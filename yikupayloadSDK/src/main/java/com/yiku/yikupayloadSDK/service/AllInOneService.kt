package com.yiku.yikupayloadSDK.service

import android.util.Log
import com.yiku.yikupayloadSDK.protocol.ALLINONE_ALLOW_DETONATE
import com.yiku.yikupayloadSDK.protocol.ALLINONE_DETONATE_HEIGHT
import com.yiku.yikupayloadSDK.protocol.ALLINONE_FLASH_SWITCH
import com.yiku.yikupayloadSDK.protocol.ALLINONE_LIGHT_LUMINANCE
import com.yiku.yikupayloadSDK.protocol.ALLINONE_LIGHT_SWITCH
import com.yiku.yikupayloadSDK.protocol.ALLINONE_RED_AND_BLUE_CONTROL
import com.yiku.yikupayloadSDK.protocol.ALLINONE_SAFETY_SWITCH
import com.yiku.yikupayloadSDK.protocol.ALLINONE_THROWER_CONTROL
import com.yiku.yikupayloadSDK.protocol.OPEN_CLOSE_LIGHT
import com.yiku.yikupayloadSDK.util.AllInOneHost
import com.yiku.yikupayloadSDK.util.Msg
import com.yiku.yikupayloadSDK.util.VehiclePlatform
import com.yiku.yikupayloadSDK.util.bytesToHex
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import kotlin.concurrent.thread

class AllInOneService : BaseMegaphoneService() {
    private val TAG = "AllInOneService";
    private val port = 8519
    private var client: Socket? = null
    private var out: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false
    private var host = ""

    override fun setIp(ip: String) {
        host = ip
        setHost(host)
    }
    override fun getIp(): String {
        return host
    }

    init {
        platform = VehiclePlatform.H16
        if (host == "") {
            host = AllInOneHost
        }
        setHost(host)
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

    override fun connect(): Boolean {
        //开启一个链接，需要指定地址和端口
        return try {
            Log.i(TAG, "连接多合一")
            client = Socket(host, port)

            out = client!!.getOutputStream()
            Log.i(TAG, "多合一连接成功")
            isConnected = true
            inputStream = client!!.getInputStream()
            thread {
                try {
                    while (client!!.isConnected) {
                        val recv = ByteArray(1024)
                        val i = inputStream?.read(recv)
                        if (i == 0) {
                            continue
                        }
//                        Log.i(TAG, "recv:${String(recv)}")
                        val data = recv.slice(0 until i!!).toByteArray()

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
                } catch (e: Exception) {
                    Log.i(TAG, "多合一信息获取失败：$e")
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
                Log.i(TAG, "多合一，sendData:${bytesToHex(data)}")
                //向输出流中写入数据，传向服务端
                if (getIsConnected()) {
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

    override fun getIsConnected(): Boolean {
        return if (client == null) {
            false
        } else (client!!.isConnected && isConnected)
    }

    // 安全开关控制
    fun safetySwitch(isOpen: Boolean) {
        val msg = Msg()
        msg.msgId = ALLINONE_SAFETY_SWITCH.toByte()
        msg.payload = ByteArray(1)
        // 打开安全开关
        if(isOpen){
            msg.payload[0] = 0x01.toByte()
        }
        // 关闭安全开关
        else {
            msg.payload[0] = 0x00.toByte()
        }
        sendData2Payload(msg.getMsg())
    }

    // 开关灯
    fun openLight(isOpen: Boolean) {
        val msg = Msg()
        msg.msgId = ALLINONE_LIGHT_SWITCH.toByte()
        msg.payload = ByteArray(1)
        // 开灯
        if(isOpen){
            msg.payload[0] = 0x01.toByte()
        }
        // 关灯
        else {
            msg.payload[0] = 0x00.toByte()
        }
        sendData2Payload(msg.getMsg())
    }

    // 亮度调节（0-30）
    fun luminanceChange(lum: Int) {
        val msg = Msg()
        msg.msgId = ALLINONE_LIGHT_LUMINANCE.toByte()
        msg.payload = ByteArray(1)
        if(lum > 30) {
            msg.payload[0] = 30.toByte()
        }
        else {
            msg.payload[0] = lum.toByte()
        }
        sendData2Payload(msg.getMsg())
    }

    // 爆闪开关
    fun flashSwitch(isOpen: Boolean) {
        val msg = Msg()
        msg.msgId = ALLINONE_FLASH_SWITCH.toByte()
        msg.payload = ByteArray(1)
        // 开灯
        if(isOpen){
            msg.payload[0] = 0x01.toByte()
        }
        // 关灯
        else {
            msg.payload[0] = 0x00.toByte()
        }
        sendData2Payload(msg.getMsg())
    }

    // 抛投开关，index:0全部，1是1号，2是2号
    fun throwerSwitch(index: Int, isOpen: Boolean) {
        val msg = Msg()
        msg.msgId = ALLINONE_THROWER_CONTROL.toByte()
        msg.payload = ByteArray(2)
        msg.payload[0] = index.toByte()
        // 开
        if(isOpen){
            msg.payload[1] = 0x01.toByte()
        }
        // 关
        else {
            msg.payload[1] = 0x00.toByte()
        }
        sendData2Payload(msg.getMsg())
    }

    // 红蓝模式控制
    override fun redBlueLedControl(model: Byte) {
        val msg = Msg()
        msg.msgId = ALLINONE_RED_AND_BLUE_CONTROL.toByte()
        msg.payload = ByteArray(2)
        msg.payload[0] = model
        sendData2Payload(msg.getMsg())
    }

    // 灭火弹充电放电，index:1是1号，2是2号
    fun allowDetonate(index: Int, isAllow: Boolean) {
        val msg = Msg()
        msg.msgId = ALLINONE_ALLOW_DETONATE.toByte()
        msg.payload = ByteArray(2)
        msg.payload[0] = index.toByte()
        // 允许引爆，充电
        if(isAllow){
            msg.payload[1] = 0x01.toByte()
        }
        // 不允许引爆，放电
        else {
            msg.payload[1] = 0x00.toByte()
        }
        sendData2Payload(msg.getMsg())
    }

    // 设置引爆高度
    fun setDetonateHeight(height: Int) {
        val msg = Msg()
        msg.msgId = ALLINONE_DETONATE_HEIGHT.toByte()
        msg.payload = ByteArray(2)
        msg.payload[0] = height.toByte()
        sendData2Payload(msg.getMsg())
    }
}