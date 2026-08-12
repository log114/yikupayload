package com.yiku.yikupayloadSDK.protocol

const val CARGOBOX_ENABLE = 0X12 // 压缩机使能，1字节，0：disable，1：enable
const val CARGOBOX_ACCELERATE = 0X13 // 压缩机转速提高，1字节：空
const val CARGOBOX_DECELERATE = 0X14 // 压缩机转速降低，1字节：空
const val CARGOBOX_STATE = 0X15 // 压缩机状态返回，200ms返回一次，7字节：压缩机使能、转速高8位、转速低8位、当前温度（4字节）