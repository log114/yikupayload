package com.yiku.yikupayloadSDK.protocol

const val SETIP_READ_IP = 0XA0 // 读取设备IP配置，查询时发1位空值，接收时10位，前4位ip，中4位网关，最后两位端口（小端）
const val SETIP_SET_IP = 0XA1 // 设置IP，10位，前4位ip，中4位网关，最后两位端口（小端）
const val SETIP_RESET = 0XA2 // 恢复出厂设置
const val SETIP_RESTART = 0XB0 // 重启设备