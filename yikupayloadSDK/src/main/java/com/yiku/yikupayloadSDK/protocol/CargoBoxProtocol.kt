package com.yiku.yikupayloadSDK.protocol

const val CARGOBOX_ENABLE = 0X12 // 压缩机使能，1字节，0：disable，1：enable
const val CARGOBOX_ACCELERATE = 0X13 // 压缩机转速提高，1字节：空
const val CARGOBOX_DECELERATE = 0X14 // 压缩机转速降低，1字节：空
/* 压缩机状态返回，200ms返回一次，11字节
* byte0: 压缩机状态：0和1都是待机，5是正常，6是错误
* byte1: 转速高8位
* byte2: 转速低8位
* byte3~byte4: 当前温度，高位在前，需除以10显示1位小数
* byte5~byte6: 当前电压，高位在前，单位mV
* byte7~byte8: 当前电流，高位在前，单位mA
* byte9~byte10: 错误码，高位在前
*/
const val CARGOBOX_STATE = 0X15