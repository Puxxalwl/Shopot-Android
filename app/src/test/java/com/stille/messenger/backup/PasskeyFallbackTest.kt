package com.stille.messenger.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeyFallbackTest {
    @Test fun supportedPasskeyKeepsPreferredPath() = runBlocking {
        assertTrue(passkeyEnrollmentOrFallback { true })
    }

    @Test fun missingPrfOffersRecoveryCode() = runBlocking {
        assertFalse(passkeyEnrollmentOrFallback { false })
    }

    @Test fun providerFailureOffersRecoveryCode() = runBlocking {
        assertFalse(passkeyEnrollmentOrFallback {
            throw PasskeyBackupKeys.PasskeyException("provider unavailable")
        })
    }

    @Test(expected = PasskeyBackupKeys.PasskeyException::class)
    fun userCancellationDoesNotStartFallback() = runBlocking<Unit> {
        passkeyEnrollmentOrFallback {
            throw PasskeyBackupKeys.PasskeyException("cancelled", cancelled = true)
        }
    }

    @Test(expected = CancellationException::class)
    fun coroutineCancellationIsNotSwallowed() = runBlocking<Unit> {
        passkeyEnrollmentOrFallback { throw CancellationException() }
    }
}
