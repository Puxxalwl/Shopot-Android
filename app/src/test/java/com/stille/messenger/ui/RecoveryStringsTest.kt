package com.stille.messenger.ui

import com.stille.messenger.backup.RecoveryController
import com.stille.messenger.ui.screens.RecoveryStrings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryStringsTest {
    @Test fun everyFailureHasShortReadableCopyInBothLanguages() {
        for (english in listOf(false, true)) {
            val strings = RecoveryStrings(english)
            for (reason in RecoveryController.Reason.entries) {
                val text = strings.error(reason)
                assertTrue(text.isNotBlank())
                assertTrue(text.length < 120)
                for (technical in listOf("backup", "PRF", "ядро", "Core", "unlock")) {
                    assertFalse(text.contains(technical, ignoreCase = true))
                }
            }
        }
    }

    @Test fun savedCopyNoticeDoesNotPromiseMessageHistory() {
        assertTrue(RecoveryStrings(false).enabled.contains("Без истории"))
        assertTrue(RecoveryStrings(true).enabled.contains("history is not included"))
    }
}
