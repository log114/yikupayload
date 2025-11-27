package com.yiku.yikupayloadSDK.service

import android.os.Build
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import kotlin.concurrent.thread
import com.yiku.yikupayloadSDK.util.ShoutHost
import com.yiku.yikupayloadSDK.util.VehiclePlatform
import com.yiku.yikupayloadSDK.util.bytesToHex
import java.net.StandardSocketOptions


class MegaphoneService : BaseMegaphoneService() {
    private val TAG = "MegaphoneService";


    //    private val ip = "192.168.144.23"


    private val port = 8519

    //    private val ip = "10.10.62.61"
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
            host = ShoutHost
        }
        setHost(host)

    }

    override fun connect(): Boolean {
        //开启一个链接，需要指定地址和端口
        return try {
            Log.i(TAG, "连接喊话器")
            client = Socket(host, port)

            out = client!!.getOutputStream()
            Log.i(TAG, "喊话器连接成功")
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
//            Log.e(TAG, "connect error:${e.message}")
//            e.printStackTrace()
//            showToast("连接失败")
            false
        }
    }

    override fun sendData2Payload(data: ByteArray): Int {
        thread {
            try {
                Log.i(TAG, "喊话器，sendData:${bytesToHex(data)}")
                //向输出流中写入数据，传向服务端
                if (client == null || !client!!.isConnected) {
                    if (!isConnected) {
                        connect()
                    }
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
}