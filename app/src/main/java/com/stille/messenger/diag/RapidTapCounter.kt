package com.stille.messenger.diag

/**
 * Detects N rapid consecutive taps (the hidden diagnostics entry in
 * Settings: five quick presses on "About Stille"). A gap longer than
 * [maxGapMs] resets the sequence; pure and unit-testable.
 */
class RapidTapCounter(
    private val requiredTaps: Int = REQUIRED_TAPS,
    private val maxGapMs: Long = MAX_GAP_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var count = 0
    private var lastTapMs = 0L

    /** Registers a tap; returns true on the completing tap. */
    fun tap(): Boolean {
        val now = clock()
        count = if (lastTapMs == 0L || now - lastTapMs <= maxGapMs) count + 1 else 1
        lastTapMs = now
        return if (count >= requiredTaps) {
            count = 0
            true
        } else {
            false
        }
    }

    companion object {
        const val REQUIRED_TAPS = 5
        const val MAX_GAP_MS = 800L
    }
}
