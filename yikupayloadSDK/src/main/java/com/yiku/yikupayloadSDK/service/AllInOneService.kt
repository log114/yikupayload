package com.yiku.yikupayloadSDK.service

import android.util.Log
import com.yiku.yikupayloadSDK.protocol.ALLINONE_ALLOW_DETONATE
import com.yiku.yikupayloadSDK.protocol.ALLINONE_DETONATE_HEIGHT
import com.yiku.yikupayloadSDK.protocol.ALLINONE_FLASH_SWITCH
import com.yiku.yikupayloadSDK.protocol.ALLINONE_LIGHT_LUMINANCE
import com.yiku.yikupayloadSDK.protocol.ALLINONE_LIGHT_SWITCH
import com.yiku.yikupayloadSDK.protocol.ALLINONE_PITCH_CONTROL
import com.yiku.yikupayloadSDK.protocol.ALLINONE_RED_AND_BLUE_CONTROL
import com.yiku.yikupayloadSDK.protocol.ALLINONE_SAFETY_SWITCH
import com.yiku.yikupayloadSDK.protocol.ALLINONE_THROWER_CONTROL
import com.yiku.yikupayloadSDK.protocol.OPEN_CLOSE_LIGHT
import com.yiku.yikupayloadSDK.util.AllInOneHost
import com.yiku.yikupayloadSDK.util.Msg
import com.yiku.yikupayloadSDK.util.MsgCallback
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
    // 照明灯、红蓝灯和抛投
    private val mainPort = 8529
    private var mainClient: Socket? = null
    private var mainOut: OutputStream? = null
    private var mainInputStream: InputStream? = null
    private var mainIsConnected = false
    var mainMsgCallbacks: List<MsgCallback> = ArrayList()
    // 俯仰
    private val ptzPort = 12345
    private var ptzClient: Socket? = null
    private var ptzOut: OutputStream? = null
    private var ptzInputStream: InputStream? = null
    private var ptzIsConnected = false
    var ptzMsgCallbacks: List<MsgCallback> = ArrayList()

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

    override fun connect(): Boolean {
        //开启一个链接，需要指定地址和端口
        return try {
            Log.i(TAG, "连接多合一喊话器")
            client = Socket(host, port)

            out = client!!.getOutputStream()
            Log.i(TAG, "多合一喊话器连接成功")
            isConnected = true
            inputStream = client!!.getInputStream()
            true
        } catch (e: Exception) {
            isConnected = false
            false
        }
    }

    override fun sendData2Payload(data: ByteArray): Int {
        thread {
            try {
                Log.i(TAG, "多合一喊话器，sendData:${bytesToHex(data)}")
                //向输出流中写入数据，传向服务端
                if (!getIsConnected()) {
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

    // 断连
    fun disConnect() {
        if(getIsConnected()) {
            isConnected = false
            client?.close()
        }
    }

    var mainParseIndex = 0;
    var mainRecvData = ByteArray(128)
    var mainRecvDataLast =  ByteArray(128)
    private fun mainParseByte(b: Byte): Boolean {
        when (mainParseIndex) {
            0 -> { // header
                if (b != 0x8d.toByte()) {
                    mainParseIndex = 0
                    mainRecvData = ByteArray(128)
                    return false
                }
                mainRecvData[0] = b
                mainParseIndex++
                return false

            }
            1 -> { //LEN
                mainRecvData[1] = b
                mainParseIndex++
                return false

            }
            2 -> { // MSG_ID
                mainRecvData[2] = b
                mainParseIndex++
                return false
            }
            else -> {
                mainRecvData[mainParseIndex] = b
                mainParseIndex++
                return if (mainParseIndex >= mainRecvData[1].toInt() + 4) {
                    mainParseIndex = 0
                    mainRecvDataLast = mainRecvData
                    mainRecvData = ByteArray(128)
                    true
                } else {
                    false
                }
            }

        }
    }
    // 照明灯、红蓝灯和抛投用端口8529
    fun mainConnect(): Boolean {
        //开启一个链接，需要指定地址和端口
        return try {
            Log.i(TAG, "连接多合一")
            mainClient = Socket(host, mainPort)

            mainOut = mainClient!!.getOutputStream()
            Log.i(TAG, "多合一连接成功")
            mainIsConnected = true
            mainInputStream = mainClient!!.getInputStream()
            thread {
                try {
                    while (mainClient!!.isConnected) {
                        val recv = ByteArray(1024)
                        val i = mainInputStream?.read(recv)
                        if (i == 0) {
                            continue
                        }
//                        Log.i(TAG, "recv:${String(recv)}")
                        val data = recv.slice(0 until i!!).toByteArray()

                        data.forEach {
                            run {
                                if (mainParseByte(it)) {
                                    for (msgCallback in mainMsgCallbacks) {
                                        msgCallback.onMsg(mainRecvDataLast)
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
            mainIsConnected = false
            false
        }
    }

    fun mainSendData2Payload(data: ByteArray): Int {
        thread {
            try {
                Log.i(TAG, "多合一，sendData:${bytesToHex(data)}")
                //向输出流中写入数据，传向服务端
                if (!getMainIsConnected()) {
                    mainConnect()
                }
                mainOut?.write(data)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                Log.e(TAG, "传输失败，重试中...")
                mainSendData2Payload(data)
            }
        }
        return 0
    }

    fun getMainIsConnected(): Boolean {
        return if (mainClient == null) {
            false
        } else (mainClient!!.isConnected && mainIsConnected)
    }

    // 断连
    fun mainDisConnect() {
        if(getIsConnected()) {
            mainIsConnected = false
            mainClient?.close()
        }
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
        mainSendData2Payload(msg.getMsg())
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
        mainSendData2Payload(msg.getMsg())
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
        mainSendData2Payload(msg.getMsg())
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
        mainSendData2Payload(msg.getMsg())
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
        mainSendData2Payload(msg.getMsg())
    }

    // 红蓝模式控制
    override fun redBlueLedControl(model: Byte) {
        val msg = Msg()
        msg.msgId = ALLINONE_RED_AND_BLUE_CONTROL.toByte()
        msg.payload = ByteArray(1)
        msg.payload[0] = model
        mainSendData2Payload(msg.getMsg())
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
        mainSendData2Payload(msg.getMsg())
    }

    // 设置引爆高度
    fun setDetonateHeight(height: Int) {
        val msg = Msg()
        msg.msgId = ALLINONE_DETONATE_HEIGHT.toByte()
        msg.payload = ByteArray(2)
        msg.payload[0] = height.toByte()
        mainSendData2Payload(msg.getMsg())
    }

    var ptzParseIndex = 0;
    var ptzRecvData = ByteArray(128)
    var ptzRecvDataLast =  ByteArray(128)
    private fun ptzParseByte(b: Byte): Boolean {
        when (ptzParseIndex) {
            0 -> { // header
                if (b != 0x8d.toByte()) {
                    ptzParseIndex = 0
                    ptzRecvData = ByteArray(128)
                    return false
                }
                ptzRecvData[0] = b
                ptzParseIndex++
                return false

            }
            1 -> { //LEN
                ptzRecvData[1] = b
                ptzParseIndex++
                return false

            }
            2 -> { // MSG_ID
                ptzRecvData[2] = b
                ptzParseIndex++
                return false
            }
            else -> {
                ptzRecvData[ptzParseIndex] = b
                ptzParseIndex++
                return if (ptzParseIndex >= ptzRecvData[1].toInt() + 4) {
                    ptzParseIndex = 0
                    ptzRecvDataLast = ptzRecvData
                    ptzRecvData = ByteArray(128)
                    true
                } else {
                    false
                }
            }

        }
    }

    fun registPtzMsgCallback(msgCallback: MsgCallback) {
        this.ptzMsgCallbacks += msgCallback
    }

    fun ptzConnect(): Boolean {
        //开启一个链接，需要指定地址和端口
        return try {
            Log.i(TAG, "连接多合一云台")
            ptzClient = Socket(host, ptzPort)

            ptzOut = ptzClient!!.getOutputStream()
            Log.i(TAG, "多合一云台连接成功")
            ptzIsConnected = true
            ptzInputStream = ptzClient!!.getInputStream()
            thread {
                try {
                    while (ptzClient!!.isConnected) {
                        val recv = ByteArray(1024)
                        val i = ptzInputStream?.read(recv)
                        if (i == 0) {
                            continue
                        }
//                        Log.i(TAG, "recv:${String(recv)}")
                        val data = recv.slice(0 until i!!).toByteArray()

                        data.forEach {
                            run {
                                if (ptzParseByte(it)) {
                                    for (msgCallback in ptzMsgCallbacks) {
                                        msgCallback.onMsg(ptzRecvDataLast)
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
            ptzIsConnected = false
            false
        }
    }

    fun ptzSendData2Payload(data: ByteArray): Int {
        thread {
            try {
                Log.i(TAG, "多合一云台，sendData:${bytesToHex(data)}")
                //向输出流中写入数据，传向服务端
                if (!getIsPtzConnected()) {
                    ptzConnect()
                }
                ptzOut?.write(data)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                Log.e(TAG, "传输失败，重试中...")
                ptzSendData2Payload(data)
            }
        }
        return 0

    }

    fun getIsPtzConnected(): Boolean {
        return if (ptzClient == null) {
            false
        } else (ptzClient!!.isConnected && ptzIsConnected)
    }

    // 云台断连
    fun disConnectPtz() {
        if(getIsPtzConnected()) {
            ptzIsConnected = false
            ptzClient?.close()
        }
    }

    // 云台俯仰控制(0-900，对应0-90°)
    fun pitchControl(pitch: Int) {
        val msg = Msg()
        msg.msgId = ALLINONE_PITCH_CONTROL.toByte()
        msg.payload = ByteArray(2).apply {
            this[0] = ((pitch ushr 8) and 0xFF).toByte()  // 高字节
            this[1] = (pitch and 0xFF).toByte()           // 低字节
        }
        ptzSendData2Payload(msg.getMsg())
    }
}