package com.yiku.yikupayload_sdk.util

interface MsgCallback {
    //    fun setId(id: String)
    fun getId(): String
    fun onMsg(msg: ByteArray)
}