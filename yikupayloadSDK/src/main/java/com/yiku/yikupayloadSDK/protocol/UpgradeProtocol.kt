package com.yiku.yikupayloadSDK.protocol

const val UPGRADE_READ_DEVICE_INFO = 0X01 // 读取设备信息
const val UPGRADE_RESET_DEVICE_INFO = 0X02 // 重置远程升级信息
const val UPGRADE_START = 0X03 // 下发远程升级请求
const val UPGRADE_TRANSMISSION_PACKAGE = 0X04 // 下发升级数据包
const val UPGRADE_PACKAGE_VERIFICATION = 0X05 // 升级包校验
const val UPGRADE_RESTART_DEVICE = 0X06 // 重启设备
