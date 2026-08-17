package com.yiku.yikupayloadSDK.protocol

const val THROWER_UPDATE = 0x0A // 抛投板子程序升级，4字节，分别是 空、空、空、空
const val THROWER_CONTROL_ONE = 0x21 // 单舵机动作控制，4字节，分别是 舵机号（0-7）、关闭/开启（0/1）、空、空
const val THROWER_CONTROL_ALL = 0x22 // 全部舵机动作控制，4字节，分别是 关闭/开启（0/1）、空、空、空
const val THROWER_CHARGING = 0x23 // 充电放电，4字节，分别是 放电/充电（0/1）、空、空、空
const val THROWER_ALLOW_DETONATION= 0x24 // 允许起爆，4字节，分别是 取消/允许起爆（0/1）、空、空、空
const val THROWER_STATE = 0x25 // 舵机状态（旧版），无需发送，每1秒自动上报一次，6字节，分别是 高度、起爆状态、充电状态、温度、总状态、起爆高度
/* 新版修改，0x25返回40个字节
* （8字节）：雷达高度、起爆高度、3号钩重量低八位、3号钩重量高八位、4号钩重量低八位、4号钩重量高八位、保留、保留
* 1号弹（8字节）：起爆充电状态（0：不允许起爆，1：允许起爆，2：正在充电，127：未连接）、高度状态（0：高度不足，1：高度足够）、保留、保留、总状态（0：无法引爆，1：可以引爆）、保留、保留、保留
* 2号弹（8字节）：起爆充电状态（0：不允许起爆，1：允许起爆，2：正在充电，127：未连接）、高度状态（0：高度不足，1：高度足够）、保留、保留、总状态（0：无法引爆，1：可以引爆）、保留、保留、保留
* 3号弹（8字节）：起爆充电状态（0：不允许起爆，1：允许起爆，2：正在充电，127：未连接）、高度状态（0：高度不足，1：高度足够）、保留、保留、总状态（0：无法引爆，1：可以引爆）、保留、保留、保留
* 4号弹（8字节）：起爆充电状态（0：不允许起爆，1：允许起爆，2：正在充电，127：未连接）、高度状态（0：高度不足，1：高度足够）、保留、保留、总状态（0：无法引爆，1：可以引爆）、保留、保留、保留
* */

const val THROWER_CONNECT_TEST = 0x26 // 连接测试，心跳包，定时发送，4字节，分别是 空、空、空、空
const val THROWER_DETONATE_HEIGHT = 0x27 // 设置起爆高度，4字节，分别是 起爆高度、空、空、空
const val THROWER_CONTROL_TWO_CENTER = 0x28 // 双舵机动作控制(中间俩)，4字节，分别是 关闭/开启（0/1）、空、空、空
const val THROWER_CONTROL_TWO_LEFT = 0x29 // 双舵机动作控制(左侧俩1/2)，4字节，分别是 关闭/开启（0/1）、空、空、空
const val THROWER_CONTROL_TWO_RIGHT = 0x2A // 双舵机动作控制(右侧俩7/8)，4字节，分别是 关闭/开启（0/1）、空、空、空
const val THROWER_CHARGING_AND_ALLOW = 0x31 // 充电放电和允许起爆一起，4字节，分别是 弹号（1-4）、状态（0/1）、空、空

// 200kg抛投器内容，包括0x25的重量反馈
const val THROWER_CALIBRATION_1 = 0x2C // 重量标定1
const val THROWER_CALIBRATION_2 = 0x2D // 重量标定2
const val THROWER_FACTORY_RESET = 0x2E // 恢复出厂设置
const val THROWER_PEEL = 0x2F // 去皮

