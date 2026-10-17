package com.stille.messenger.ui

/** Mirrors core apiSetName / invite.max_name: 1..32 UTF-8 bytes. */
object ProfileName {
    const val MAX_BYTES = 32

    fun byteLength(value: String): Int = value.toByteArray(Charsets.UTF_8).size

    fun isValid(value: String): Boolean = byteLength(value.trim()) in 1..MAX_BYTES

    /** Keep a complete Unicode code-point prefix; never split a surrogate pair. */
    fun limitInput(value: String): String {
        var end = 0
        var bytes = 0
        while (end < value.length) {
            val next = end + Character.charCount(value.codePointAt(end))
            val size = byteLength(value.substring(end, next))
            if (bytes + size > MAX_BYTES) break
            bytes += size
            end = next
        }
        return value.substring(0, end)
    }
}
