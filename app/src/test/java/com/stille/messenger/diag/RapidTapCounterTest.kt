package com.stille.messenger.diag

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RapidTapCounterTest {

    @Test
    fun `five rapid taps trigger once`() {
        var now = 0L
        val counter = RapidTapCounter(clock = { now })
        repeat(4) { assertFalse(counter.tap()) }
        now += 100
        assertTrue(counter.tap())
    }

    @Test
    fun `slow taps never trigger`() {
        var now = 0L
        val counter = RapidTapCounter(clock = { now })
        repeat(20) {
            assertFalse(counter.tap())
            now += RapidTapCounter.MAX_GAP_MS + 1
        }
    }

    @Test
    fun `the counter resets after a trigger`() {
        var now = 0L
        val counter = RapidTapCounter(clock = { now })
        repeat(5) { now += 50; counter.tap() }
        now += 50
        assertFalse(counter.tap())
        repeat(3) { now += 50; assertFalse(counter.tap()) }
        now += 50
        assertTrue(counter.tap())
    }

    @Test
    fun `a single slow tap restarts the sequence`() {
        var now = 0L
        val counter = RapidTapCounter(clock = { now })
        repeat(3) { now += 50; counter.tap() }
        now += 5000
        assertFalse(counter.tap())
        now += 50
        assertFalse(counter.tap())
        now += 50
        assertFalse(counter.tap())
        now += 50
        assertFalse(counter.tap())
        now += 50
        assertTrue(counter.tap())
    }
}
