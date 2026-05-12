package com.yiku.yikupayloadSDK.protocol

/* 缓降器安全开关
* 发送1个字节
* byte0: 0: 关，1: 开
* */
const val DESCENT200_CONTROL = 0x01

/* 缓降器红蓝警示灯
* 发送1个字节
* byte0: 0: 关，1: 开
* */
const val DESCENT200_WARNING_LIGHT = 0x02

/* 缓降器紧急控制
* 发送1个字节
* byte0: 0: 复位，1：急停，2：熔断
* */
const val DESCENT200_EMERGENCY_CONTROL = 0x03

/* 缓降器速度控制（-100~100cm/s）
* 发送1个字节
* byte0: 速度（-100~100cm/s）
* */
const val DESCENT200_SPEED_CONTROL = 0x04

/* 缓降器长度控制（0~100m）
* 发送1个字节
* byte0: 放线长度（0~100m）
* */
const val DESCENT200_LENGTH_CONTROL = 0x05

/* 缓降器挂钩开关
* 发送1个字节
* byte0: 0: 关，1: 开
* */
const val DESCENT200_HOOK_CONTROL = 0x06

/* 缓降器重量清零
* 发送1个字节
* byte0: 留空
* */
const val DESCENT200_RESET_WEIGHT_CONTROL = 0x30


/* 缓降器状态返回
* 无需发送，返回16个字节
* byte0: 安全开关状态，0：关，1: 开
* byte1: 触顶状态，0: 未触顶，1: 触顶
* byte2: 红蓝指示灯状态，0：关，1: 开
* byte3: 吊载重量高位（0-300kg）
* byte4: 吊载重量低位
* byte5: 水平摆角高位（0-359°）
* byte6: 水平摆角低位
* byte7: 缓降钩速度（-100~100cm/s），负值上升，正值下降，0停止
* byte8: 释放绳长高位（单位是0.1m）
* byte9: 释放绳长低位
* byte10: 缓降钩开关状态，0：关，1: 开
* byte11: 缓降钩通信状态，0：正常，1: 断连
* byte12: 缓降钩电压高位（单位0.01伏）
* byte13: 缓降钩电压低位
* byte14: 主板温度（-40℃~150℃）
* byte15: 保留
* */
const val DESCENT200_STATE_GET = 0x90