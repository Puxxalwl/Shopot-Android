package com.stille.messenger.ui.qr

/**
 * The supplied Stille symbol, not a substitute icon or the wordmark.
 *
 * Derived numerically (no visual inspection) from drawable-nodpi/stille_logo.png,
 * SHA-256 9803262ce220cb2418efdb9072cca36c20bead4b2daae2f34a8ee9b12daab0e8.
 * At alpha >= 128 the dominant connected component occupies x=405..853,
 * y=247..885. The smaller wordmark components start at y=958, separated by
 * transparent rows. Only the upper component's 449x639 crop is used here.
 * Its alpha silhouette is sampled at pixel centers to 45x64, thresholded at
 * 128, and painted solid black. RGB shading and all lower components are
 * intentionally discarded. Each row's least significant bit is its left edge.
 * This packed source mask keeps the renderer and its tests Android-independent.
 */
internal object StilleSymbol {
    const val width = 45
    const val height = 64

    fun isBlack(x: Int, y: Int): Boolean = rows[y] and (1L shl x) != 0L

    private val rows = longArrayOf(
        0x0000fff00000L, 0x0007ffff0000L, 0x001fffffe000L, 0x007ffffffc00L,
        0x00fffffffe00L, 0x01ffffffff80L, 0x01ffffffffc0L, 0x03ffffffffe0L,
        0x03fffffffff0L, 0x03fffffffff8L, 0x07fffffffffcL, 0x07fffffffffcL,
        0x07fffffffffeL, 0x07fffffffffeL, 0x03ffffffffffL, 0x03ffffffffffL,
        0x03ffffffffffL, 0x01ffffffffffL, 0x00ffffffffffL, 0x007fffffffffL,
        0x003fffffffffL, 0x000fffffffffL, 0x0003fe1ffffeL, 0x00000007fffeL,
        0x00000001fffeL, 0x000000007ffcL, 0x00000000fffcL, 0x000000fffff8L,
        0x00000ffffff0L, 0x00007ffffff0L, 0x0001ffffffe0L, 0x0007ffffffc0L,
        0x001fffffff00L, 0x003ffffffe00L, 0x00fffffff800L, 0x01fffffff000L,
        0x03ffffffc000L, 0x03ffffff0000L, 0x07fffff80000L, 0x07ffffe00000L,
        0x0fffffc00000L, 0x0fffffc04000L, 0x1fffffdffe00L, 0x1fffffffff80L,
        0x1fffffffffe0L, 0x1ffffffffff0L, 0x1ffffffffff8L, 0x1ffffffffffcL,
        0x1ffffffffffeL, 0x1ffffffffffeL, 0x0fffffffffffL, 0x0fffffffffffL,
        0x0fffffffffffL, 0x07ffffffffffL, 0x07fffffffffeL, 0x03fffffffffeL,
        0x01fffffffffcL, 0x00fffffffffcL, 0x007ffffffff8L, 0x003fffffffe0L,
        0x001fffffffc0L, 0x0007ffffff00L, 0x0001fffff800L, 0x00000fff8000L,
    )
}
