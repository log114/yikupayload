package com.yiku.yikupayloadSDK.protocol
/* 0x25状态反馈，6字节，自动上报，周期300ms
* byte0: 空
* byte1: 激光状态，00：关，01：开
* byte2: 舵机角度，单位：°
* byte3~byte4: 电压，高位在前，单位：V，电压>400V表示可以发射
* byte5: 捕捉网是否安装到位，00：安装到位，01：安装不到位
* */
const val EMNETGUN_STATUS = 0x25
const val EMNETGUN_HEARTBEAT = 0x26 // 心跳包，0字节
const val EMNETGUN_LAUNCH = 0x2D // 发射，1字节（空）
const val EMNETGUN_PITCH = 0x2E // 俯仰控制，1字节，限制0°~90°
const val EMNETGUN_LASER = 0x2F // 激光控制，1字节：00关，01开