package com.yiku.yikupayloadSDK.service

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioRecord.STATE_INITIALIZED
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import com.yiku.yikupayloadSDK.protocol.ALARM_PLAY
import com.yiku.yikupayloadSDK.protocol.AUDIO_DEL
import com.yiku.yikupayloadSDK.protocol.AUDIO_PLAY
import com.yiku.yikupayloadSDK.protocol.DISABLE_RADIO
import com.yiku.yikupayloadSDK.protocol.GET_AUDIO_FILES
import com.yiku.yikupayloadSDK.protocol.REAL_TIME_SHOUT
import com.yiku.yikupayloadSDK.protocol.REBOOT
import com.yiku.yikupayloadSDK.protocol.RESTART_RADIO
import com.yiku.yikupayloadSDK.protocol.SET_VOLUME
import com.yiku.yikupayloadSDK.protocol.START_RADIO
import com.yiku.yikupayloadSDK.protocol.STOP_ALARM_PLAY
import com.yiku.yikupayloadSDK.protocol.STOP_AUDIO_PLAY
import com.yiku.yikupayloadSDK.protocol.STOP_RADIO
import com.yiku.yikupayloadSDK.protocol.STOP_REAL_TIME_SHOUT
import com.yiku.yikupayloadSDK.protocol.STOP_TTS_LOOP_PLAY
import com.yiku.yikupayloadSDK.protocol.TTS_LOOP_PLAY
import com.yiku.yikupayloadSDK.protocol.TTS_LOOP_PLAY_V2
import com.yiku.yikupayloadSDK.protocol.TTS_PLAY
import com.yiku.yikupayloadSDK.protocol.TTS_PLAY_V2
import com.yiku.yikupayloadSDK.util.MsgCallback
import com.yiku.yikupayloadSDK.util.MsgRecv
import com.yiku.yikupayloadSDK.util.OpusUtils
import com.yiku.yikupayloadSDK.util.Uilts
import com.yiku.yikupayloadSDK.util.VehiclePlatform
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.util.*
import kotlin.concurrent.thread
import okhttp3.OkHttpClient
import okhttp3.Request
import com.alibaba.fastjson.JSONObject
import com.yiku.yikupayloadSDK.protocol.TTS_SPEECH_RATE
import com.yiku.yikupayloadSDK.util.FarendProvider
import com.yiku.yikupayloadSDK.util.ProgressRequestBody
import com.yiku.yikupayloadSDK.util.SpeexAecProcessor
import com.yiku.yikupayloadSDK.util.Uilts.normalizeExtensionToLowerCase
import com.yiku.yikupayloadSDK.util.bytesToHex
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import java.util.concurrent.TimeUnit


interface UploadFileCallback {
    fun onUploadPackageSuccess(totalNum: Int, finishNum: Int);
}


interface GetAudioFilesCallback {
    fun onResult(files: String);
}


open class BaseMegaphoneService {
    private var loopTTSPlaying: Boolean = false
    private val TAG = "BaseMegaphoneService"
    private var sharedPreferences: SharedPreferences? = null
    private var recordFile = File.createTempFile("temp", ".pcm") // 最后需要转wav
    var msgCallbacks: List<MsgCallback> = ArrayList()

    lateinit var platform: VehiclePlatform
    var isRecording: Boolean = false
    var isStartRecord: Boolean = false
    var mAudioRecord: AudioRecord? = null
    var isPlayAlarm = false
    var getAudioFilesCallback: GetAudioFilesCallback? = null

    private val farendProvider = FarendProvider()
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)  // 连接超时：30秒
            .readTimeout(60, TimeUnit.SECONDS)      // 读取超时：60秒（1分钟）
            .writeTimeout(300, TimeUnit.SECONDS)     // 写入超时：300秒（5分钟）
            .build()
    }
    private var host = ""
    private var needsReinitialization = false

    private var context: Context? = null

    // 添加线程同步对象
    private val audioLock = Any()

    // 保存录音线程引用
    private var recordingThread: Thread? = null

    // 添加音频状态标志
    private var audioInitialized = false

    var onRecordingReady: (() -> Unit)? = null

    private lateinit var servoControlOut: OutputStream
    private var servoControlClient: Socket? = null
    open fun registMsgCallback(msgCallback: MsgCallback) {
        this.msgCallbacks += msgCallback
    }

    open fun unRegistMsgCallback(id: String) {
//        this.msgCallbacks += msgCallback
        val callbacks = ArrayList<MsgCallback>()
        for (callback in this.msgCallbacks) {
            if (id == callback.getId()) {
                continue
            }
            callbacks += callback
        }
        this.msgCallbacks = callbacks
    }

    open fun setContext(newContext: Context) {
        context = newContext
    }

    open fun connect(): Boolean {
        return false
    }

    open fun connect(callback: MsgRecv): Boolean {
        return false
    }

    open fun getIsConnected(): Boolean {
        return false
    }

    open fun getIsConnectedYA3(): Boolean {
        return false
    }

    open fun setIp(ip: String) {

    }
    open fun getIp(): String {
        return host
    }

    open fun setHost(host: String) {
        this.host = host
    }

    open fun getHost(): String {
        return host
    }

    open fun sendData2Payload(data: ByteArray): Int {
        return 0
    }


    private fun connectServoControlServer(): Boolean {
        if (servoControlClient != null && servoControlClient!!.isConnected) {
            return true
        }
        if (getHost().isEmpty()) {
            Log.e(TAG,"host未初始化")
        }
        //开启一个链接，需要指定地址和端口
        return try {
            servoControlClient = Socket(getHost(), 12345)
            servoControlOut = servoControlClient!!.getOutputStream()
            true
        } catch (e: Exception) {
//            Log.e(TAG, "connect error:${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * 喊话器舵机控制，占空比范围80-220
     */
    open fun servoControl(dutyCycle: UInt) {
        Log.i(TAG, "servoControl....")
        val dc = dutyCycle + 80u
        if (!connectServoControlServer()) {
            Log.i(TAG, "not connect....")
            Log.i(TAG,"该版本设备不支持APP控制俯仰")
            return
        }
        val data: UInt = if (dc < 80u) {
            80u
        } else (if (dc > 220u) {
            220u
        } else {
            dc
        })

        val sendData = ByteArray(2)
        sendData[0] = 0x8d.toByte()
        sendData[1] = data.toByte()
        Log.i(TAG, "sendData:${bytesToHex(sendData)}")
        servoControlOut.write(sendData)
    }

    fun playAlarm() {
        val sendData = ALARM_PLAY.toByteArray()
        // 播放警报
        isPlayAlarm = true
        sendData2Payload(sendData)
    }
    // 连接测试，发送一条无用消息
    fun connectTest() {
        val sendData = "[TEST]".toByteArray()
        sendData2Payload(sendData)
    }

    fun reboot(){
        val sendData = REBOOT.toByteArray()
        sendData2Payload(sendData)
    }

    fun stopPlayAlarm() {
        val sendData = STOP_ALARM_PLAY.toByteArray()
        // 播放警报
        isPlayAlarm = false
        sendData2Payload(sendData)
    }

    fun setVolume(volume: Int) {
        var sendData = SET_VOLUME.toByteArray()
        val arr = Integer.toHexString(volume).toByteArray()
        if (arr.size == 1) {
            // 个位数特殊处理
            sendData += '0'.code.toByte();
            sendData += arr[0]
        } else {
            sendData += arr[0]
            sendData += arr[1]
        }
        sendData2Payload(sendData)
    }

    fun disableRadio() {
        val sendData = DISABLE_RADIO.toByteArray()
        sendData2Payload(sendData)
    }
    fun restartRadio() {
        val sendData = RESTART_RADIO.toByteArray()
        sendData2Payload(sendData)
    }

    // 设置tts播放语速
    fun setTtsSpeechRate(speechRate: Int) {
        var sendData = TTS_SPEECH_RATE.toByteArray()
        sendData += speechRate.toString().toByteArray()
        sendData2Payload(sendData)
    }

    // 输入参考帧
    open fun inputReferenceFrame(pcm16k: ShortArray) {
        farendProvider.onBeforePlay(pcm16k)
    }

    @RequiresPermission(value = "android.permission.RECORD_AUDIO")
    open fun startRealTimeShout(isOpenAECM: Boolean = true, initialDelay: Int = 120) {
        synchronized(audioLock) {
            if (isRecording) { Log.w(TAG, "已经在录音状态，忽略重复启动"); return }

            farendProvider.clear()

            val wantUnprocessed = true
            val audioSource = if (wantUnprocessed)
                MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC
            val rate = 8000
            val track = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = 960
            Log.i(TAG, "startRecord src=$audioSource ...")

            if (needsReinitialization || mAudioRecord == null) {
                releaseAudioResources()
                if (!isMicrophoneAvailable(audioSource, rate, track, audioFormat, bufferSize)) {
                    Log.e(TAG, "麦克风不可用")
                    isRecording = true; return
                }
                try {
                    mAudioRecord = AudioRecord(audioSource, rate, track, audioFormat, bufferSize).apply {
                        if (state != STATE_INITIALIZED) throw IllegalStateException("init fail")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "fallback MIC", e)
                    mAudioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC, rate, track, audioFormat, bufferSize
                    ).apply { if (state != STATE_INITIALIZED) throw IllegalStateException("MIC also fail") }
                }
                needsReinitialization = false
                mAudioRecord!!.startRecording()
            }

            val data = ByteArray(bufferSize)
            try {
                isRecording = true
                onRecordingReady?.invoke()
                val opusUtils = OpusUtils.getInstant()

                val localSpeex  = if (isOpenAECM) SpeexAecProcessor(8000, 80, 250, true)  else null
                val remoteSpeex = SpeexAecProcessor(8000, 80, 480, false)

                recordingThread = thread {
                    val enc = opusUtils.createEncoder(rate, 1, 1)
                    var smoothLocalDelay = initialDelay
                    var lastLocalFar = ShortArray(80)

                    var smoothEnergy = 0L
                    var suddenCount = 0      // 突发翻倍计数
                    var creepCount  = 0      // 缓增计数（抓嗡嗡）
                    var muteUntil = 0L
                    val lastSent = ShortArray(480)
                    val sentFrame = ShortArray(80)   // 复用，少分配

                    while (isRecording && !Thread.interrupted()) {
                        val now = System.currentTimeMillis()

                        // 拆上一轮真实发出的 PCM，等下逐帧 playback
                        // （lastSent 初始化是全零，前 350ms 参考=静音，AEC 安全）

                        // 1) 读 MIC
                        val read = mAudioRecord!!.read(data, 0, bufferSize)
                        if (read <= 0) continue
                        val nearAll = Uilts.byteArrayToShortArray(data)
                        val blockEnergy = nearAll.fold(0L) { a, s -> a + s.toInt() * s.toInt() }
                        val nearSilent = blockEnergy < 200_000_000L
                        val outAll = ShortArray(480)

                        for (i in 0 until 6) {
                            // 取这一帧的历史参考（上一轮 lastSent 里对应位置）
                            System.arraycopy(lastSent, i * 80, sentFrame, 0, 80)

                            val nf = if (nearSilent) ShortArray(80)
                            else nearAll.copyOfRange(i * 80, i * 80 + 80)

                            // ★ 帧内严格交替：先 playback 一帧 far，再 capture 一帧 near
                            remoteSpeex.playback(sentFrame)

                            if (isOpenAECM) {
                                val hit = farendProvider.pollOne(now - smoothLocalDelay, 18)
                                val lf = if (hit != null) {
                                    val meas = (now - hit.enqueueTimeMs).toInt().coerceIn(40, 350)
                                    smoothLocalDelay = (smoothLocalDelay * 9 + meas) / 10
                                    hit.pcm
                                } else lastLocalFar
                                localSpeex?.playback(lf)
                                lastLocalFar = lf
                            }

                            val locOut = if (isOpenAECM) localSpeex!!.process(nf) else nf
                            val remOut = remoteSpeex.process(nf)      // 内部 speex_echo_capture
                            val mrg = if (isOpenAECM) {
                                val eL = locOut.fold(0L) { a, s -> a + s.toInt() * s.toInt() }
                                val eR = remOut.fold(0L) { a, s -> a + s.toInt() * s.toInt() }
                                if (eL < eR) locOut else remOut
                            } else remOut
                            mrg.copyInto(outAll, i * 80)
                        }

                        // ==================== Guard V3 ====================
                        val energy = outAll.fold(0L) { a, s -> a + s.toInt() * s.toInt() }
                        val old = smoothEnergy
                        smoothEnergy = (smoothEnergy * 7 + energy * 3) / 10

                        // 模式 A：真·突发（拍麦/爆音/瞬间啸叫尖峰）
                        // 你机器满幅块能量 ~5e11，正常说话 5e9~2e10，门槛放 2e10 以上才不误杀
                        val sudden = energy > 25_000_000_000L &&
                                energy > old * 3 &&
                                old > 2_000_000_000L

                        // 模式 B：缓增嗡嗡（回声正反馈慢慢爬，每帧 +20~40%）
                        val creeping = energy > 4_000_000_000L &&
                                energy > old * 1.18 &&
                                old > 1_000_000_000L

                        var isMuted = false
                        if (now < muteUntil) {
                            outAll.fill(0); isMuted = true
                        } else if (sudden) {
                            suddenCount++
                            creepCount = 0
                            if (suddenCount >= 4) {          // 40ms 连续突发 → 确认
                                muteUntil = now + 500L
                                suddenCount = 0
                                outAll.fill(0); isMuted = true
                                Log.w(TAG, "HOWL sudden mute  energy=$energy  old=$old")
                            }
                        } else if (creeping) {
                            creepCount++
                            suddenCount = 0
                            if (creepCount >= 10) {          // 连续 100ms 缓增 → 抓嗡嗡
                                muteUntil = now + 600L
                                creepCount = 0
                                outAll.fill(0); isMuted = true
                                Log.w(TAG, "HOWL creep mute   energy=$energy  old=$old  cnt=$creepCount")
                            }
                        } else {
                            // 能量掉下去了才衰减计数，避免说话停顿误清
                            if (energy < old * 0.7) {
                                suddenCount = maxOf(0, suddenCount - 2)
                                creepCount  = maxOf(0, creepCount - 3)
                            }
                        }

                        // 噪声门（极静时发零，但参考已经在顶部 playback 过了）
                        val finalOut = if (!isMuted && blockEnergy < 100_000_000L) ShortArray(480) else outAll

                        // 编码发送
                        val ret = ByteArray(bufferSize / 8)
                        opusUtils.encode(enc, finalOut, 0, ret)
                        val send = REAL_TIME_SHOUT.toByteArray() + ret
                        try { sendData2Payload(send) } catch (_: IOException) {}

                        // 保存本轮真实发出去的东西 → 下轮拆帧 playback
                        System.arraycopy(finalOut, 0, lastSent, 0, 480)

                        try { Thread.sleep(10) } catch (_: InterruptedException) { break }
                    }
                    opusUtils.destroyEncoder(enc)
                    localSpeex?.release()
                    remoteSpeex.release()
                }
            } catch (e: IllegalStateException) {
                needsReinitialization = true
                Log.w(TAG, "re-init", e)
                startRealTimeShout(isOpenAECM, initialDelay)
            }
        }
    }

    fun stopRealTimeShout() {
        synchronized(audioLock) {
            isRecording = false

            stopRecordingThread()

            // 发送停止标识，关闭喊话器的功放
            val sendData = STOP_REAL_TIME_SHOUT.toByteArray()
            sendData2Payload(sendData)
        }
    }

    private fun stopRecordingThread() {
        try {
            // 添加线程中断机制，确保线程结束
            recordingThread?.interrupt()
            // 停止线程
            recordingThread?.join(200)
            recordingThread = null
            Log.i(TAG, "录音线程已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止录音线程失败", e)
        }
    }

    // 用于最终释放实时喊话音频资源
    open fun releaseAudioResources() {
        synchronized(audioLock) {
            try {
                Log.i(TAG, "释放所有音频资源")

                // 停止录音线程
                stopRecordingThread()

                // 释放AudioRecord
                if (mAudioRecord != null) {
                    try {
                        // 停止录音
                        if (mAudioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            mAudioRecord?.stop()
                        }
                        mAudioRecord?.release()
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "释放异常: ${e.message}")
                    } finally {
                        Log.d(TAG, "AudioRecord状态: ${mAudioRecord?.state}")
                        mAudioRecord = null  // 强制置空
                        Log.i(TAG, "AudioRecord已释放")
                    }
                }

                isRecording = false
            } catch (e: Exception) {
                Log.e(TAG, "释放资源失败", e)
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun isMicrophoneAvailable(audioSource: Int, sampleRate: Int, channelConfig: Int, audioFormat: Int, bufferSize: Int): Boolean {
        // 对于低版本Android，使用简化的检查
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return isMicrophoneAvailableLegacy(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)
        }

        // Android 12+ 使用完整的AppOpsManager检查
        return try {
            // 检查 Context 是否可用
            if (context == null) {
                Log.e(TAG, "Context is null, cannot check microphone availability")
                return false
            }

            // 获取 AppOpsManager
            val appOps = context!!.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            // 创建 AttributionSource（适用于 Android 12+）
            val attributionSource = context!!.createAttributionContext(context!!.packageName).attributionSource

            // 启动 RECORD_AUDIO 操作
            val result = appOps.startOpNoThrow(
                AppOpsManager.OPSTR_RECORD_AUDIO,
                attributionSource.uid,
                attributionSource.packageName ?: "",
                attributionSource.attributionTag ?: "",
                null
            )

            // 检查结果
            if (result != AppOpsManager.MODE_ALLOWED) {
                Log.e(TAG, "AppOpsManager explicitly denied record audio operation. Result code: $result")
                return false
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception in microphone availability check", e)
            false
        }
    }

    // 低版本Android的简化检查
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun isMicrophoneAvailableLegacy(audioSource: Int, sampleRate: Int, channelConfig: Int, audioFormat: Int, bufferSize: Int): Boolean {
        var audioRecord: AudioRecord? = null
        return try {
            audioRecord = AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            audioRecord.state == AudioRecord.STATE_INITIALIZED
        } catch (e: Exception) {
            Log.e(TAG, "Legacy microphone check failed", e)
            false
        } finally {
            audioRecord?.release()
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    open fun startRecord() {
        var audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION //来源
        if (platform == VehiclePlatform.H30) {
            audioSource = MediaRecorder.AudioSource.MIC //来源
        }
        val rate = 8000 //采样频率
        val track = AudioFormat.CHANNEL_IN_MONO //声道
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT //格式
        var bufferSize = 960
        if (platform == VehiclePlatform.H30) {
            bufferSize = 640
        }
        Log.i(TAG, "startRecord...")
        mAudioRecord = AudioRecord(
            audioSource, rate,
            track, audioFormat, bufferSize
        )
        val data = ByteArray(bufferSize)
        mAudioRecord!!.startRecording()
        isStartRecord = true

        val writer = FileWriter(recordFile)
        writer.write("")// 清空文件内容

        thread {
            while (isStartRecord) {
                val read = mAudioRecord!!.read(data, 0, bufferSize)
                writer.write(data.toString())
                Thread.sleep(10)
            }
            writer.close()
            println("录音文件大小: " + recordFile.length())
        }
    }

    open fun stopRecord() {
        isStartRecord = false
        mAudioRecord?.release()
        mAudioRecord = null
    }

    open fun getRecordFile(): File? {
        return recordFile;
    }

    fun tts(ttsText: String) {
        var sendData = TTS_PLAY.toByteArray()
        sendData += ttsText.encodeToByteArray();
        sendData2Payload(sendData)
    }


    fun startLoopTts(ttsText: String) {
        var sendData = TTS_LOOP_PLAY.toByteArray()
        sendData += ttsText.encodeToByteArray()
        sendData2Payload(sendData)
        loopTTSPlaying = true
    }

    fun ttsV2(ttsText: String, voice: Int) {
        var sendData = TTS_PLAY_V2.toByteArray()
        sendData += voice.toString().toByteArray() + ttsText.encodeToByteArray();
        sendData2Payload(sendData)
    }


    fun startLoopTtsV2(ttsText: String, voice: Int) {
        var sendData = TTS_LOOP_PLAY_V2.toByteArray()
        sendData += voice.toString().toByteArray() + ttsText.encodeToByteArray()
        sendData2Payload(sendData)
        loopTTSPlaying = true
    }

    fun stopLoopTts() {
        val sendData = STOP_TTS_LOOP_PLAY.toByteArray()
        sendData2Payload(sendData)
        loopTTSPlaying = false
    }

    @Deprecated(message = "该方法已弃用，请使用uploadFileForHttp")
    open fun uploadFile(bArrs: List<ByteArray>, callback: UploadFileCallback?) {
        uploadFile(bArrs, 0, callback)
    }

    @Deprecated(message = "该方法已弃用，请使用uploadFileForHttp")
    private fun uploadFile(bArrs: List<ByteArray>, packageNum: Int, callback: UploadFileCallback?) {
//        var sendNum = 0;
//        Log.i(
//            TAG,
//            "uploadFile packageNum:${packageNum}, data:${bArrs[packageNum].contentToString()}"
//        )
//        if (packageNum % 1000 == 0) {
//            System.gc()
//        }
        sendData2Payload(bArrs[packageNum])
        if (packageNum + 1 == bArrs.size) {
            Log.i(TAG, "uploadFile完成...")
            return
        }
        callback?.onUploadPackageSuccess(bArrs.size, packageNum + 1)
        Thread.sleep(100)
        uploadFile(bArrs, packageNum + 1, callback)
    }


    @Deprecated(message = "该方法已弃用，请使用delFile")
    fun delAudio(fileName: String) {
        var sendData = AUDIO_DEL.toByteArray()
        sendData += fileName.encodeToByteArray()
        sendData2Payload(sendData)
    }

    @Deprecated(message = "该方法已弃用，请使用fetchFiles")
    fun getAudioList(callback: GetAudioFilesCallback) {
        getAudioFilesCallback = callback
        sendData2Payload(GET_AUDIO_FILES.toByteArray())

    }

    fun playAudio(audioName: String) {
        stopPlayAudio()// 先关闭之前播放的内容
        Thread.sleep(200)
        val sendData = "${AUDIO_PLAY}0".toByteArray() + audioName.toByteArray()
        sendData2Payload(sendData)
    }

    fun stopPlayAudio() {
        sendData2Payload(STOP_AUDIO_PLAY.toByteArray())
    }

    fun startLoopPlayAudio(audioName: String) {
        val sendData = "${AUDIO_PLAY}1".toByteArray() + audioName.toByteArray()
        sendData2Payload(sendData)
    }

    fun stopLoopPlayAudio() {
        stopPlayAudio()
    }

    fun startRadio() {
        val sendData = START_RADIO.toByteArray()
        sendData2Payload(sendData)
    }

    fun stopRadio() {
        val sendData = STOP_RADIO.toByteArray()
        sendData2Payload(sendData)
        farendProvider.clear() // 停止收音时，清除参考帧
    }

    fun fetchFiles(): ArrayList<String>? {
        val request =
            Request.Builder().url("http://" + getHost() + ":8222/fetch-files").get().build()

        val response = client.newCall(request).execute()
        val respStr = response.body?.string()
        Log.i(TAG, "获取数据：${respStr}")
        val jsonResp = JSONObject.parseObject(respStr)
        if (jsonResp.getIntValue("code") == 0) {
            return jsonResp.getObject("data", ArrayList<String>().javaClass)
        }
        return null
    }

    fun delFile(fileName: String): Boolean {
        val formBody = FormBody.Builder().add("filename", fileName).build()
        val request =
            Request.Builder().url("http://" + getHost() + ":8222/del-file").post(formBody).build()

        val response = client.newCall(request).execute()
        val jsonResp = JSONObject.parseObject(response.body?.string())
        return jsonResp.getIntValue("code") == 0
    }

    fun uploadFile(
        file: File,
        callback: ProgressRequestBody.ProgressCallback
    ): Boolean {
        val requestBody: MultipartBody.Builder =
            MultipartBody.Builder().setType(MultipartBody.FORM) //文件和json参数共同上传
        val MEDIA_TYPE_MARKDOWN: MediaType? = "text/x-markdown; charset=utf-8".toMediaTypeOrNull()
        if (file != null) { //添加文件到form-data
//            val body = RequestBody.create(MEDIA_TYPE_MARKDOWN, file)
//            // 参数分别为， 请求key ，文件名称 ， RequestBody
//            requestBody.addFormDataPart("file", file.name, body)
            // 强制将文件后缀名改为小写
            val normalizedFileName = file.normalizeExtensionToLowerCase()
            // 🔥 关键修改：使用您的 ProgressRequestBody 而不是默认的 RequestBody
            val body = ProgressRequestBody(MEDIA_TYPE_MARKDOWN!!, file, callback)

            // 参数分别为：请求key，文件名称，RequestBody
            requestBody.addFormDataPart("file", normalizedFileName, body)
        }
        val request =
            Request.Builder()
                .url("http://" + getHost() + ":8222/upload-file")
                .post(requestBody.build())
                .build()
        val response = client.newCall(request).execute()
        val jsonResp = JSONObject.parseObject(response.body?.string())
        if (jsonResp.getIntValue("code") == 0) {
            return true
        }
        Log.i(TAG, "错误代号:${jsonResp.getIntValue("code")}")
        return false
    }

    // 四合一的灯光控制
    open fun openLight(open: Int, query: Boolean) {}
    open fun luminanceChange(lum: Int, query: Boolean) {}
    open fun sharpFlash(open: Int, query: Boolean) {}
    open fun fetchTemperature() {}
    open fun redBlueLedControl(model: Byte) {}
    open fun controlServo(cval: Int) {}
}