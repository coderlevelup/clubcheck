package app.clubcheck.core

/** CRC-16/CCITT-FALSE: init 0xFFFF, poly 0x1021, no reflection, no final xor. */
fun crc16(data: ByteArray, length: Int = data.size): Int {
    var crc = 0xFFFF
    for (i in 0 until length) {
        crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
        repeat(8) { crc = if (crc and 0x8000 != 0) ((crc shl 1) xor 0x1021) and 0xFFFF else (crc shl 1) and 0xFFFF }
    }
    return crc
}
