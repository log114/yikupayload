package com.yiku.yikupayloadSDK.service

import android.util.Log
import com.yiku.yikupayloadSDK.protocol.FOURINONE2_FLASH_SWITCH
import com.yiku.yikupayloadSDK.protocol.FOURINONE2_LIGHT_LUMINANCE
import com.yiku.yikupayloadSDK.protocol.FOURINONE2_LIGHT_SWITCH
import com.yiku.yikupayloadSDK.protocol.FOURINONE2_RED_AND_BLUE_CONTROL
import com.yiku.yikupayloadSDK.util.FourInOne2Host
import com.yiku.yikupayloadSDK.util.Msg
import com.yiku.yikupayloadSDK.util.MsgCallback
import com.yiku.yikupayloadSDK.util.VehiclePlatform
import com.yiku.yikupayloadSDK.util.bytesToHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import kotlin.collections.plus
import kotlin.concurrent.thread

class FourInOne2Service : BaseMegaphoneService() {
    private val TAG = "FourInOne2Service";
    private val port = 8519
    private var client: Socket? = null
    private var out: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false
    private var host = ""
    // 照明灯、红蓝灯
    private val mainPort = 8529
    private var mainClient: Socket? = null
    private var mainOut: OutputStream? = null
    private var mainInputStream: InputStream? = null
    private var mainIsConnected = false
    var mainMsgCallbacks: List<MsgCallback> = ArrayList()

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
            host = FourInOne2Host
        }
        setHost(host)
    }

    override fun connect(): Boolean {
        //开启一个链接，需要指定地址和端口
        return try {
            Log.i(TAG, "连接四合一二喊话器")
            client = Socket(host, port)

            out = client!!.getOutputStream()
            Log.i(TAG, "四合一二喊话器连接成功")
            isConnected = true
            inputStream = client!!.getInputStream()
            thread {
//                Log.i(TAG, "recv start...")
                try {
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
                                        // 使用协程并发，不阻塞当前线程
                                        CoroutineScope(Dispatchers.IO).launch {
                                            msgCallback.onMsg(tmp)
                                        }
                                    }
                                }
                                tmp = ByteArray(0);
                            }
                            tmp += it
                        }
                        if (tmp.isNotEmpty() && tmp.size >= 4) {
                            for (msgCallback in msgCallbacks) {
                                // 使用协程并发，不阻塞当前线程
                                CoroutineScope(Dispatchers.IO).launch {
                                    msgCallback.onMsg(tmp)
                                }
                            }
                            tmp = ByteArray(0);
                        }
                        if (String(recv).startsWith("GAF")) {
                            getAudioFilesCallback?.onResult(String(recv).substring(3))
                        }
                    }
                } catch (e: Exception) {
                    Log.i(TAG, "喊话器信息获取失败：$e")
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
                Log.i(TAG, "四合一二喊话器，sendData:${bytesToHex(data)}")
                //向输出流中写入数据，传向服务端
                if (!getIsConnected()) {
                    Log.d(TAG, "未发送，喊话器未连接")
                    return@thread
                }
                out?.write(data)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                Log.e(TAG, "传输失败，可能连接已断开...")
                disConnect()
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

    fun registMainMsgCallback(msgCallback: MsgCallback) {
        this.mainMsgCallbacks += msgCallback
    }
    // 照明灯、红蓝灯和抛投用端口8529
    fun mainConnect(): Boolean {
        //开启一个链接，需要指定地址和端口
        return try {
            Log.i(TAG, "连接四合一二")
            mainClient = Socket(host, mainPort)

            mainOut = mainClient!!.getOutputStream()
            Log.i(TAG, "四合一二代连接成功")
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
                    Log.i(TAG, "四合一二代信息获取失败：$e")
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
                Log.i(TAG, "四合一二代，sendData:${bytesToHex(data)}")
                //向输出流中写入数据，传向服务端
                if (!getMainIsConnected()) {
                    Log.d(TAG, "main未连接，不发送")
                    return@thread
                }
                mainOut?.write(data)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                Log.e(TAG, "传输失败，可能是main连接已断开...")
                mainDisConnect()
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
        if(getMainIsConnected()) {
            mainIsConnected = false
            if (mainOut != null) {
                mainOut?.close()
            }
            if (mainInputStream != null) {
                mainInputStream?.close()
            }
            if (mainClient != null && mainClient?.isConnected == true) {
                mainClient?.close()
            }
        }
    }

    // 开关灯
    fun openLight(isOpen: Boolean) {
        val msg = Msg()
        msg.msgId = FOURINONE2_LIGHT_SWITCH.toByte()
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
        msg.msgId = FOURINONE2_LIGHT_LUMINANCE.toByte()
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
        msg.msgId = FOURINONE2_FLASH_SWITCH.toByte()
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

    // 红蓝模式控制
    override fun redBlueLedControl(model: Byte) {
        val msg = Msg()
        msg.msgId = FOURINONE2_RED_AND_BLUE_CONTROL.toByte()
        msg.payload = ByteArray(1)
        msg.payload[0] = model
        mainSendData2Payload(msg.getMsg())
    }
}