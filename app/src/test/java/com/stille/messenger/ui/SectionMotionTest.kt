package com.stille.messenger.ui

import com.stille.messenger.ui.components.sectionPageScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionMotionTest {
    @Test fun restingPageIsFullSize() {
        assertEquals(1f, sectionPageScale(0f), 0f)
        assertEquals(0.992f, sectionPageScale(1f), 0.00001f)
    }

    @Test fun scaleIsBoundedAndIndependentOfDirection() {
        for (i in 0..100) {
            val offset = i / 100f
            val scale = sectionPageScale(offset)
            assertTrue(scale in 0.992f..1f)
            assertEquals(scale, sectionPageScale(-offset), 0f)
        }
        assertEquals(0.992f, sectionPageScale(-2f), 0.00001f)
        assertEquals(0.992f, sectionPageScale(2f), 0.00001f)
    }
}
