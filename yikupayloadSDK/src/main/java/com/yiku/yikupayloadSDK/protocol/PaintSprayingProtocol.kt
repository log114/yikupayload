package com.yiku.yikupayloadSDK.protocol

/* 0x25状态反馈，2字节，自动上报，周期300ms
* byte0: 距离档位，0-3
* byte1: 流量档位，0-3
* */
const val PAINTSPRAYING_STATUS = 0x25
const val PAINTSPRAYING_CONTROL = 0x2C // 双路舵机档位控制，2字节：距离档位（0-3），流量档位（0-3）