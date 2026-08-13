package com.yiku.yikupayloadSDK.util

// 大端序，Int转ByteArray
fun int16ToByteArray(i: Int): ByteArray {
    val arr = ByteArray(2)
    arr[0] = ((i shr 8) and 0xFF).toByte()  // 高字节
    arr[1] = (i and 0xFF).toByte()          // 低字节
    return arr
}

// 大端序，ByteArray转Int
fun byteArrayToInt16(arr: ByteArray): Int {
    if (arr.size < 2) throw IllegalArgumentException("数组长度至少为2")
    return ((arr[0].toInt() and 0xFF) shl 8) or (arr[1].toInt() and 0xFF)
}

fun bytesToHex(bytes: ByteArray): String {
    val hexChars = CharArray(bytes.size * 2)
    var hex = ""
    for (b in bytes) {
        val st = String.format("%02X", b)
        hex += "$st "
    }
    return hex
}

// 小端序，Int转ByteArray
fun int16ToByteArrayLE(i: Int): ByteArray {
    val arr = ByteArray(2)
    arr[0] = (i and 0xff).toByte()       // 低字节
    arr[1] = ((i shr 8) and 0xff).toByte() // 高字节
    return arr
}

// 小端序，ByteArray转Int
fun byteArrayToInt16LE(arr: ByteArray): Int {
    require(arr.size == 2) { "Array size must be 2" }
    val low = arr[0].toInt() and 0xFF          // 低字节，无符号化
    val high = (arr[1].toInt() and 0xFF) shl 8 // 高字节，左移8位
    return high or low                         // 组合
}

/**
 * 将 IPv4 字符串（如 "192.168.144.26"）转换为 4 字节的 ByteArray
 */
fun ipStringToByteArray(ip: String): ByteArray {
    return ip.split(".")
        .map { it.toInt().toByte() }  // toInt() 自动校验数字格式，toByte() 截断为 -128~127
        .toByteArray()
}

/**
 * 将 4 字节的 ByteArray 转换为 IPv4 字符串（如 "192.168.144.26"）
 * @throws IllegalArgumentException 如果数组长度不为 4
 */
fun byteArrayToIpString(bytes: ByteArray): String {
    require(bytes.size == 4) { "ByteArray must have exactly 4 bytes for IPv4" }
    return bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
}