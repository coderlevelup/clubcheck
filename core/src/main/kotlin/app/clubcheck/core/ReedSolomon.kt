package app.clubcheck.core

import com.google.zxing.common.reedsolomon.GenericGF
import com.google.zxing.common.reedsolomon.ReedSolomonDecoder
import com.google.zxing.common.reedsolomon.ReedSolomonEncoder
import com.google.zxing.common.reedsolomon.ReedSolomonException

/** Reed-Solomon over GF(256), primitive 0x11D, first root 0, [PARITY] parity bytes appended (matches reedsolo). */
object ReedSolomon {
    const val PARITY = 40
    private val field = GenericGF.QR_CODE_FIELD_256

    fun encode(data: ByteArray): ByteArray {
        val ints = IntArray(data.size + PARITY)
        for (i in data.indices) ints[i] = data[i].toInt() and 0xFF
        ReedSolomonEncoder(field).encode(ints, PARITY)
        return ByteArray(ints.size) { ints[it].toByte() }
    }

    /** Returns the corrected data bytes, or null when the damage is beyond repair. */
    fun decode(coded: ByteArray): ByteArray? {
        if (coded.size <= PARITY) return null
        val ints = IntArray(coded.size) { coded[it].toInt() and 0xFF }
        return try {
            ReedSolomonDecoder(field).decode(ints, PARITY)
            ByteArray(coded.size - PARITY) { ints[it].toByte() }
        } catch (e: ReedSolomonException) {
            null
        }
    }
}
