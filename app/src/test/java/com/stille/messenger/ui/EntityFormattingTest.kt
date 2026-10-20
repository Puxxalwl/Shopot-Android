package com.stille.messenger.ui

import com.stille.messenger.ui.components.utf8EntityRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntityFormattingTest {
    @Test fun mapsUtf8OffsetsToComposeUtf16Indices() {
        val text = "aé😀z"
        assertEquals(1..1, utf8EntityRange(text, 1, 2))
        assertEquals(2..3, utf8EntityRange(text, 3, 4))
        assertEquals(4..4, utf8EntityRange(text, 7, 1))
    }

    @Test fun rejectsMalformedAndNonBoundaryRanges() {
        val text = "é😀"
        assertNull(utf8EntityRange(text, -1, 1))
        assertNull(utf8EntityRange(text, 0, 0))
        // Byte 1 is inside the two-byte é: not a code-point boundary.
        assertNull(utf8EntityRange(text, 1, 1))
        // Byte 4 is inside the four-byte 😀; 5+2 overruns the body.
        assertNull(utf8EntityRange(text, 4, 2))
        assertNull(utf8EntityRange(text, 5, 2))
    }

    @Test fun mapsWholeEmojiToUtf16Pair() {
        // Bytes 2..6 of "é😀" are exactly 😀: one code point, two UTF-16 chars.
        assertEquals(1..2, utf8EntityRange("é😀", 2, 4))
    }
}
