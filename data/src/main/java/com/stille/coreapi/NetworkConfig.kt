package com.stille.coreapi

/** Binary core configuration blob. Unknown flag bits and invalid mode/address combinations fail closed. */
data class ConfigBlob(
    val flags: Int,
    val serverAddr: String = "",
    val torAddr: String = "127.0.0.1:9050",
    val ver: Int = 1
) {
    val serverMode get() = flags and SERVER_MODE != 0
    val useFakeTls get() = flags and USE_FAKE_TLS != 0
    val useTorMask get() = flags and USE_TOR_MASK != 0
    val deviceSync get() = flags and DEVICE_SYNC != 0
    val lanDiscovery get() = flags and LAN_DISCOVERY != 0
    val autodial get() = flags and AUTODIAL != 0
    val natPunch get() = flags and NAT_PUNCH != 0

    fun withNatPunch(enabled: Boolean): ConfigBlob = copy(
        flags = if (enabled) flags or NAT_PUNCH else flags and NAT_PUNCH.inv()
    )

    fun encode(): ByteArray {
        validate()
        return FrameBuffer().apply {
            require(ver == 1) { "unsupported core config version" }
            writeU8(ver)
            writeU32(flags.toLong())
            writeStr16(serverAddr)
            writeStr16(torAddr)
        }.toByteArray()
    }

    private fun validate() {
        require(flags and KNOWN_FLAGS.inv() == 0) { "unknown core config flags" }
        require(serverAddr.encodeToByteArray().size <= Limits.CONFIG_ADDRESS_MAX) { "server address too long" }
        require(torAddr.encodeToByteArray().size <= Limits.CONFIG_ADDRESS_MAX) { "Tor address too long" }
        require(!serverMode || validAddress(serverAddr)) { "server mode needs a host:port address" }
        require(!useTorMask || validAddress(torAddr)) { "Tor masking needs a host:port address" }
        if (serverAddr.isNotEmpty()) require(validAddress(serverAddr)) { "invalid server address" }
        if (torAddr.isNotEmpty()) require(validAddress(torAddr)) { "invalid Tor address" }
    }

    companion object {
        const val SERVER_MODE = 1 shl 0
        const val USE_FAKE_TLS = 1 shl 1
        const val USE_TOR_MASK = 1 shl 2
        const val DEVICE_SYNC = 1 shl 3
        const val LAN_DISCOVERY = 1 shl 4
        const val AUTODIAL = 1 shl 5
        const val NAT_PUNCH = 1 shl 6
        const val KNOWN_FLAGS = 0x7f
        val DEFAULT = ConfigBlob(USE_FAKE_TLS or LAN_DISCOVERY or AUTODIAL or NAT_PUNCH)

        fun decode(payload: ByteArray): ConfigBlob {
            val r = FrameReader(payload)
            val version = r.readU8()
            require(version == 1) { "unsupported core config version" }
            val config = ConfigBlob(r.readU32().toInt(), r.readStr16(), r.readStr16(), version)
            require(r.remaining == 0) { "trailing core config bytes" }
            config.validate()
            return config
        }

        private fun validAddress(value: String): Boolean {
            if (value.isEmpty() || value.encodeToByteArray().size > Limits.CONFIG_ADDRESS_MAX) return false
            val colon = value.lastIndexOf(':')
            if (colon <= 0 || colon == value.lastIndex) return false
            val port = value.substring(colon + 1).toIntOrNull() ?: return false
            return port in 1..65535
        }
    }
}
