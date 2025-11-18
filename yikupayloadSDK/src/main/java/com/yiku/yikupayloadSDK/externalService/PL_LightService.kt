package com.yiku.yikupayloadSDK.externalService

import android.util.Log
import com.yiku.yikupayloadSDK.util.MsgCallback
import com.yiku.yikupayloadSDK.util.PLLightHost
import com.yiku.yikupayloadSDK.util.PL_Msg
import com.yiku.yikupayloadSDK.util.bytesToHex
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.ArrayList
import java.util.Date
import kotlin.concurrent.thread

open class PL_LightService {
    private val TAG = "PL_LightService"
    var msgCallbacks: List<MsgCallback> = ArrayList()
    private val port = 8519
    private var client: Socket? = null
    private var out: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false
    private var host = ""

    private val PTZCONTROL = 0x30.toByte()
    private val LEDCONTROL = 0x2C.toByte()

    open fun setIp(ip: String) {
        host = ip
    }
    open fun getIp(): String {
        return host
    }

    open fun getIsConnected(): Boolean {
        return isConnected
    }

    open fun registMsgCallback(msgCallback: MsgCallback) {
        this.msgCallbacks += msgCallback
    }

    open fun reConnect(): Boolean {
        Log.i(TAG, "Reconnect....")
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
        return connect()
    }

    open fun connect(): Boolean {
        //开启一个链接，需要指定地址和端口
        return try {
            if(host == ""){
                host = PLLightHost
            }
            client = Socket(host, port)
            out = client?.getOutputStream()
            Log.i(TAG, "品灵探照灯连接成功")
            isConnected = true
            inputStream = client?.getInputStream()
            thread {
                try {
                    Log.i(TAG, "recv start...")
                    isConnected = true
                    while (client?.isConnected == true) {
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
                    e.printStackTrace()
                }
            }.start()
            true
        } catch (e: Exception) {
            isConnected = false
            false
        }
    }

    open fun sendData2Payload(data: ByteArray): Int {
        thread {
            try {
                Log.i(TAG, "品灵探照灯，sendData:${bytesToHex(data)}")
                //向输出流中写入数据，传向服务端
                val firstTime = Date().time
                if (!isConnected || !client?.isConnected!!) {
                    Log.i(
                        TAG,
                        "重新连接: isConnected:${isConnected}"
                    )
                    reConnect()
                }
                if (out == null) {
                    Log.i(TAG, "out is null")
                    return@thread
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

    /**
     * 云台回中
     */
    open fun PTZToCenter() {
        val msg = PL_Msg()
        msg.msgId = PTZCONTROL
        val sendData = ByteArray(14)
        sendData[0] = 0x04
        msg.payload = sendData
        sendData2Payload(msg.getMsg())
    }

    /**
     * 云台控制，手动速度模式
     */
    open fun PTZCtrlBySpeed(yawSpeed: Int, picthSpeed: Int, rollSpeed: Int) {
        val msg = PL_Msg()
        msg.msgId = PTZCONTROL
        val sendData = ByteArray(14)
        val yawSpeedData = speedToTwoByteArray(yawSpeed)
        val picthSpeedData = speedToTwoByteArray(picthSpeed)
        val rollSpeedData = speedToTwoByteArray(rollSpeed)
        sendData[0] = 0x01 // 手动速度模式
        yawSpeedData.copyInto(sendData, 1)
        picthSpeedData.copyInto(sendData, 3)
        rollSpeedData.copyInto(sendData, 5)

        msg.payload = sendData
        sendData2Payload(msg.getMsg())
    }

    /**
     * 云台控制，绝对角度控制，回中位置为0点
     */
    open fun PTZCtrlByAngle(yaw: Int, picth: Int, roll: Int) {
        val msg = PL_Msg()
        msg.msgId = PTZCONTROL
        val sendData = ByteArray(14)
        val yawData = angleToTwoByteArray(yaw)
        val picthData = angleToTwoByteArray(picth)
        val rollData = angleToTwoByteArray(roll)
        sendData[0] = 0x0B // 手动绝对角度模式
        yawData.copyInto(sendData, 1)
        picthData.copyInto(sendData, 3)
        rollData.copyInto(sendData, 5)

        msg.payload = sendData
        sendData2Payload(msg.getMsg())
    }

    /**
     * 开关灯控制
     * open 开关状态 false关 true开
     */
    open fun openLight(open: Boolean) {
        val msg = PL_Msg()
        msg.msgId = LEDCONTROL
        val sendData = ByteArray(3)
        sendData[0] = 0x74
        if (open) {
            sendData[1] = 0x01
        } else {
            sendData[1] = 0x02
        }
        msg.payload = sendData
        sendData2Payload(msg.getMsg())
    }

    /**
     * 闪烁频率设置
     * frequency 闪烁频率，2、5、10、15Hz
     */
    open fun setFlashingFrequency(frequency: Int) {
        val msg = PL_Msg()
        msg.msgId = LEDCONTROL
        val sendData = ByteArray(3)
        sendData[0] = 0x74
        sendData[1] = 0x0B
        sendData[2] = frequency.toByte()
        msg.payload = sendData
        sendData2Payload(msg.getMsg())
    }

    /**
     * 打开闪烁模式（没有关闭命令，关闭LDE再重启LDE的时候，会变回常量模式）
     */
    open fun startFlashing() {
        val msg = PL_Msg()
        msg.msgId = LEDCONTROL
        val sendData = ByteArray(3)
        sendData[0] = 0x74
        sendData[1] = 0x09
        msg.payload = sendData
        sendData2Payload(msg.getMsg())
    }

    /**
     * 亮度调整
     *
     * lum 亮度值 0-100
     */
    open fun luminanceChange(lum: Int) {
        val msg = PL_Msg()
        msg.msgId = LEDCONTROL
        val sendData = ByteArray(3)
        sendData[0] = 0x74
        sendData[1] = 0x0A
        sendData[2] = lum.toByte()
        msg.payload = sendData
        sendData2Payload(msg.getMsg())
    }

    /**
    * 速度转byteArray，1bit=0.01°
    * */
    fun speedToTwoByteArray(speed: Int): ByteArray {
        val value = speed * 100
        // 直接对32位Int进行位运算，提取高8位和低8位
        return byteArrayOf(
            ((value ushr 8) and 0xFF).toByte(),  // 获取第8-15位（高8位）
            (value and 0xFF).toByte()            // 获取第0-7位（低8位）
        )
    }

    /**
     * 角度转byteArray，1bit=360/65536°
     * angle值应该在-180到179之间
     * */
    fun angleToTwoByteArray(angle: Int): ByteArray {
        var value = 0
        if(angle > 179) {
            value = (angle - 360) * 65536 / 360
        }
        else if(angle < -180){
            value = (angle + 360) * 65536 / 360
        }
        else {
            value = angle * 65536 / 360
        }
        // 直接对32位Int进行位运算，提取高8位和低8位
        return byteArrayOf(
            ((value ushr 8) and 0xFF).toByte(),  // 获取第8-15位（高8位）
            (value and 0xFF).toByte()            // 获取第0-7位（低8位）
        )
    }
}