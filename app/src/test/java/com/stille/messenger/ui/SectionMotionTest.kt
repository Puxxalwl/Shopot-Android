package com.stille.messenger.ui

import com.stille.messenger.ui.components.sectionBlurProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionMotionTest {
    @Test fun endpointsAreSharpAndMidpointIsSoft() {
        assertEquals(0f, sectionBlurProgress(0f), 0f)
        assertEquals(0f, sectionBlurProgress(1f), 0f)
        assertEquals(1f, sectionBlurProgress(0.5f), 0f)
    }
    @Test fun progressIsBoundedSymmetricAndIndependentOfDirection() {
        for (i in 0..100) {
            val offset = i / 100f
            val progress = sectionBlurProgress(offset)
            assertTrue(progress in 0f..1f)
            assertEquals(progress, sectionBlurProgress(1f - offset), 0.00001f)
        }
        assertEquals(0f, sectionBlurProgress(-2f), 0f)
        assertEquals(0f, sectionBlurProgress(2f), 0f)
    }
}
