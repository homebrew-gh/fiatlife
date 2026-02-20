package com.fiatlife.app.data.nostr

/**
 * Minimal Bech32 decoder for Nostr key formats (npub/nsec).
 * Based on BIP-173 specification.
 */
object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    fun decode(bech32: String): Pair<String, ByteArray>? {
        val lower = bech32.lowercase()
        val pos = lower.lastIndexOf('1')
        if (pos < 1 || pos + 7 > lower.length) return null
        val hrp = lower.substring(0, pos)
        val dataPart = lower.substring(pos + 1)
        val values = ByteArray(dataPart.length)
        for (i in dataPart.indices) {
            val idx = CHARSET.indexOf(dataPart[i])
            if (idx < 0) return null
            values[i] = idx.toByte()
        }
        if (!verifyChecksum(hrp, values)) return null
        val data = convertBits(values.copyOfRange(0, values.size - 6), 5, 8, false) ?: return null
        return hrp to data
    }

    private fun polymod(values: ByteArray): Int {
        var chk = 1
        val gen = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        for (v in values) {
            val b = chk shr 25
            chk = (chk and 0x1ffffff) shl 5 xor (v.toInt() and 0xff)
            for (i in 0..4) if ((b shr i) and 1 != 0) chk = chk xor gen[i]
        }
        return chk
    }

    private fun verifyChecksum(hrp: String, data: ByteArray): Boolean {
        return polymod(hrpExpand(hrp) + data) == 1
    }

    private fun hrpExpand(hrp: String): ByteArray {
        val out = ByteArray(hrp.length * 2 + 1)
        for (i in hrp.indices) {
            out[i] = (hrp[i].code shr 5).toByte()
            out[hrp.length + 1 + i] = (hrp[i].code and 31).toByte()
        }
        return out
    }

    private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray? {
        var acc = 0
        var bits = 0
        val result = mutableListOf<Int>()
        val maxv = (1 shl toBits) - 1
        for (value in data) {
            acc = (acc shl fromBits) or (value.toInt() and 0xff)
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add((acc shr bits) and maxv)
            }
        }
        if (pad && bits > 0) result.add((acc shl (toBits - bits)) and maxv)
        else if (bits >= fromBits || ((acc shl (toBits - bits)) and maxv) != 0) return null
        return result.map { it.toByte() }.toByteArray()
    }
}

/**
 * Decode nsec1... bech32 string to 32-byte private key, or return hex as-is.
 * Returns null if the input is invalid.
 */
fun nsecToBytes(nsecOrHex: String): ByteArray? {
    val trimmed = nsecOrHex.trim()
    if (trimmed.startsWith("nsec1", ignoreCase = true)) {
        val decoded = Bech32.decode(trimmed) ?: return null
        if (decoded.first != "nsec" || decoded.second.size != 32) return null
        return decoded.second
    }
    if (trimmed.length == 64 && trimmed.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        return trimmed.hexToByteArray()
    }
    return null
}

/**
 * Normalizes pubkey from Amber (may be hex or npub) to 64-char hex.
 */
fun amberPubkeyToHex(value: String): String {
    val trimmed = value.trim()
    if (trimmed.length == 64 && trimmed.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        return trimmed
    }
    if (trimmed.startsWith("npub1", ignoreCase = true)) {
        val decoded = Bech32.decode(trimmed) ?: return trimmed
        if (decoded.first == "npub" && decoded.second.size == 32) {
            return decoded.second.toHex()
        }
    }
    return trimmed
}
