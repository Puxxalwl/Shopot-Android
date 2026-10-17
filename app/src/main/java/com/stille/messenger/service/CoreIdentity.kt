package com.stille.messenger.service

import java.io.File
import java.io.IOException

/** Never regenerate an existing identity: it also protects access to encrypted history. */
internal fun needsCoreIdentity(seed: File): Boolean {
    if (!seed.exists()) return true
    if (!seed.isFile || seed.length() != 32L) throw IOException("Invalid existing core identity")
    return false
}
