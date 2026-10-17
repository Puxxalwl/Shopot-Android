package com.stille.messenger.service

import com.stille.coreapi.Limits
import java.security.MessageDigest

/** UI status only; never persisted in a Bundle or on disk. */
data class AvatarState(val busy: Boolean = false, val saved: Boolean = false, val error: AvatarError? = null)
enum class AvatarError { CORE_UNAVAILABLE, INVALID_IMAGE, REQUEST_FAILED }

/** Content addressing defined by CORE-API: SHA-256(data)[0..16], not identity authentication. */
internal fun matchesAvatarId(id: ByteArray, data: ByteArray): Boolean =
    id.size == 16 && data.size in 1..Limits.AVATAR_PAYLOAD &&
        MessageDigest.getInstance("SHA-256").digest(data).copyOf(16).contentEquals(id)

/** Immutable snapshots, maximum 20 images including the pinned own avatar at key 0. */
internal fun cacheAvatar(current: Map<Int, ByteArray>, cid: Int, data: ByteArray): Map<Int, ByteArray> {
    require(data.size in 1..Limits.AVATAR_PAYLOAD)
    val next = LinkedHashMap(current)
    next.remove(cid)
    next[cid] = data.copyOf()
    while (next.size > 20) next.remove(next.keys.first { it != 0 })
    return next.toMap()
}
