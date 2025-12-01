package com.yiku.yikupayloadSDK.protocol

const val ALLINONE_SAFETY_SWITCH = 0x00 // 多合一安全开关
const val ALLINONE_LIGHT_SWITCH = 0x01 // 多合一照明开关
const val ALLINONE_LIGHT_LUMINANCE = 0x02 // 多合一灯光亮度(0-30)
const val ALLINONE_FLASH_SWITCH = 0x03 // 多合一爆闪开关
const val ALLINONE_THROWER_CONTROL = 0x04 // 多合一抛投控制
const val ALLINONE_RED_AND_BLUE_CONTROL = 0x07 // 多合一红蓝控制
const val ALLINONE_ALLOW_DETONATE = 0x31 // 多合一灭火弹充电放电
const val ALLINONE_DETONATE_HEIGHT = 0x27 // 设置引爆高度

/* 0x25，状态返回，payload有24字节
* 0-7：雷达高度（m），起爆高度（m），抛投钩开关状态（需要位运算，如：0010表示2号开1号关）,
* 探照灯状态（要位运算，低位在前，0-5：亮度，6：爆闪开关，7：灯开关。如：0xCA转成二进制是0b11001010，表示在开灯，在开爆闪，‘001010’表示亮度是10），警示灯开关，保留，保留，安全开关
* 8-15：1号弹状态：起爆充电状态（0：不允许，1：允许，2：正在充电，127：未连接），高度状态（0：高度不足，1：高度充足），保留，保留，总状态（0：无法引爆，1：可以引爆），保留，保留，保留
* 16-23：2号弹状态：起爆充电状态（0：不允许，1：允许，2：正在充电，127：未连接），高度状态（0：高度不足，1：高度充足），保留，保留，总状态（0：无法引爆，1：可以引爆），保留，保留，保留
* */
const val ALLINONE_STATE = 0x25 // 多合一状态返回

// 俯仰控制，端口：12345
const val ALLINONE_PITCH_CONTROL = 0x10 // 俯仰控制
// 每500ms上报一次，payload包含两个字节，高位在前，数值是0-900，对应0-90度
const val ALLINONE_PITCH_STATE = 0x11 // 俯仰角度上报（500ms）