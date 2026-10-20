package com.stille.coreapi

/**
 * Short identity codes (CORE-API 2.8.0 §4.2): 9 Crockford-base32 characters —
 * 8 data characters carrying the first 40 bits of SHA-256(bundle) plus 1
 * positional checksum character. The code is an **identifier, not a secret**:
 * it grants no rights, does not reveal the bundle and is computed by the core
 * (GENERATE_SHORT_CODE) — this object never derives codes, it only mirrors the
 * core's `invite.zig` normalization so a deeplink segment can be validated
 * locally before a DECODE_SHORT_CODE round trip.
 *
 * Alphabet: 0-9 A-H J K M N P-T V-Z (ambiguous I/L/O/U excluded).
 * Typo mapping on input: I/i/L/l → 1, O/o → 0, U/u → V; ASCII case is folded.
 */
object ShortCode {

    const val LENGTH = 9
    private const val DATA_LENGTH = LENGTH - 1
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /**
     * Normalizes case and common Crockford typos and verifies the checksum.
     * Returns the canonical uppercase code, or null when the input is not
     * exactly 9 characters, contains characters outside the alphabet/typo
     * set, or the checksum character does not match the data characters.
     */
    fun normalize(input: String): String? {
        if (input.length != LENGTH) return null
        val chars = CharArray(LENGTH)
        for (index in input.indices) {
            chars[index] = when (val c = input[index]) {
                in '0'..'9', in 'A'..'H', 'J', 'K', in 'M'..'N', in 'P'..'T', in 'V'..'Z' -> c
                in 'a'..'h', 'j', 'k', in 'm'..'n', in 'p'..'t', in 'v'..'z' -> c - 32
                'I', 'i', 'L', 'l' -> '1'
                'O', 'o' -> '0'
                'U', 'u' -> 'V'
                else -> return null
            }
        }
        if (chars[LENGTH - 1] != ALPHABET[checksum(chars)]) return null
        return String(chars)
    }

    /**
     * Positional checksum: sum of (position + 1) × alphabet index over the 8
     * data characters, mod 32 — identical to core `shortCheck`.
     */
    private fun checksum(chars: CharArray): Int {
        var acc = 0
        for (i in 0 until DATA_LENGTH) {
            val value = ALPHABET.indexOf(chars[i])
            if (value < 0) return -1
            acc = (acc + (i + 1) * value) and 31
        }
        return acc
    }
}
