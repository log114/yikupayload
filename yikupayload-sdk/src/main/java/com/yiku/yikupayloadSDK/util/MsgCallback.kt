package com.yiku.yikupayloadSDK.util

interface MsgCallback {
    //    fun setId(id: String)
    fun getId(): String
    fun onMsg(msg: ByteArray)
}