package com.stille.messenger.backup

import android.accounts.Account
import android.app.Activity
import android.content.Context
import com.stille.coreapi.GeneratedCoreApi
import com.stille.messenger.service.StilleService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * User-facing recovery flow (enable / restore / delete) on top of the
 * core-owned backup (CORE-API.md §4.10) and the user's own Google Drive
 * (CLIENT_RULES.md §4). The client never sees the blob's contents: it
 * derives the 32-byte unlock secret (passkey PRF or recovery code), lets
 * the core seal/open the blob, and stores blob + public sidecar in Drive.
 *
 * Enable (old device, provisioned profile):
 * ```
 * создать passkey → вывести секрет → CREATE_RECOVERY_BACKUP → upload blob+sidecar
 * ```
 *
 * Restore (new device, daemon in unprovisioned recovery mode):
 * ```
 * скачать blob+sidecar → подтвердить passkey / ввести код →
 * RESTORE_RECOVERY_BACKUP → тот же аккаунт, пустая история
 * ```
 */
class RecoveryController(
    private val context: Context,
    private val passkeys: PasskeyBackupKeys,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    enum class Phase { IDLE, CREATING_PASSKEY, DERIVING_SECRET, SEALING, UPLOADING, DOWNLOADING, RESTORING, DELETING }

    sealed interface Result {
        data class BackupReady(val replaced: Boolean) : Result
        /** PRF unavailable at enrollment: show the generated code once. */
        data class RecoveryCode(val code: ByteArray) : Result
        data class Restored(val name: String) : Result
        /** The blob was sealed with a recovery code (passkey path unavailable). */
        data object NeedsRecoveryCode : Result
        data object Done : Result
        data class Failed(val reason: Reason) : Result
    }

    enum class Reason {
        NO_GOOGLE_ACCOUNT, AUTH, NETWORK, NO_BACKUP, WRONG_SECRET,
        ALREADY_PROVISIONED, NOT_INITIALIZED, CORE_UNAVAILABLE, TOO_BIG, CANCELLED, PASSKEY_UNAVAILABLE, ERROR
    }

    /** Core status failure with its mapped reason (see [Reason]). */
    private class CoreRecoveryException(val reason: Reason) : Exception()

    private val _state = MutableStateFlow(Phase.IDLE)
    val state = _state.asStateFlow()

    /** The currently downloaded (blob, sidecar) pair during a restore. */
    private var pending: Pair<ByteArray, RecoveryMetadata.Metadata>? = null

    /** Whether recovery was enabled on this device. */
    val enabled: Boolean
        get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── enable / update (old device) ────────────────────────────────────

    /**
     * One-shot enable: creates the passkey, derives the unlock secret via
     * PRF, asks the core to seal the package and uploads blob + sidecar.
     * Re-enabling reuses the existing passkey instead of registering a
     * second one. Requires the bound, provisioned [service].
     */
    fun enable(activity: Activity, account: Account, service: StilleService?, onDone: (Result) -> Unit) {
        if (service == null) {
            onDone(Result.Failed(Reason.CORE_UNAVAILABLE))
            return
        }
        scope.launch {
            var phase = Phase.CREATING_PASSKEY
            try {
                val hadPasskey = prefs().getInt(KEY_SOURCE, 0) == RecoveryMetadata.KEY_SOURCE_PASSKEY
                if (!hadPasskey) {
                    _state.value = phase
                    val supportsPasskey = passkeyEnrollmentOrFallback {
                        passkeys.createPasskey(activity)
                    }
                    if (!supportsPasskey) {
                        _state.value = Phase.IDLE
                        // Fallback: a random recovery code shown to the user once.
                        onDone(Result.RecoveryCode(RecoveryCode.generate()))
                        return@launch
                    }
                }
                phase = Phase.DERIVING_SECRET
                _state.value = phase
                val salt = RecoveryMetadata.randomPrfSalt()
                val secret = try {
                    passkeys.deriveUnlockSecret(activity, salt)
                } catch (e: PasskeyBackupKeys.PasskeyException) {
                    if (e.cancelled) throw e
                    _state.value = Phase.IDLE
                    onDone(Result.RecoveryCode(RecoveryCode.generate()))
                    return@launch
                }
                phase = Phase.SEALING
                _state.value = phase
                val blob = seal(service, secret)
                phase = Phase.UPLOADING
                _state.value = phase
                val replaced = upload(account, blob, RecoveryMetadata.Metadata(
                    keySource = RecoveryMetadata.KEY_SOURCE_PASSKEY,
                    prfSalt = salt,
                    createdMs = System.currentTimeMillis(),
                    apiVersion = GeneratedCoreApi.API_VERSION
                ), activity)
                AccountManagerDriveTokens.saveAccountName(context, account.name)
                prefs().edit().putBoolean(KEY_ENABLED, true)
                    .putInt(KEY_SOURCE, RecoveryMetadata.KEY_SOURCE_PASSKEY).apply()
                _state.value = Phase.IDLE
                onDone(Result.BackupReady(replaced))
            } catch (e: CancellationException) {
                _state.value = Phase.IDLE
                throw e
            } catch (e: Exception) {
                _state.value = Phase.IDLE
                onDone(Result.Failed(reasonOf(e)))
            }
        }
    }

    /** Recovery-code path: seals with the code the user was shown once. */
    fun enableWithRecoveryCode(activity: Activity, account: Account, service: StilleService?, code: ByteArray, onDone: (Result) -> Unit) {
        if (service == null) {
            onDone(Result.Failed(Reason.CORE_UNAVAILABLE))
            return
        }
        scope.launch {
            var phase = Phase.SEALING
            try {
                _state.value = phase
                val blob = seal(service, code)
                phase = Phase.UPLOADING
                _state.value = phase
                val replaced = upload(account, blob, RecoveryMetadata.Metadata(
                    keySource = RecoveryMetadata.KEY_SOURCE_RECOVERY_CODE,
                    prfSalt = null,
                    createdMs = System.currentTimeMillis(),
                    apiVersion = GeneratedCoreApi.API_VERSION
                ), activity)
                AccountManagerDriveTokens.saveAccountName(context, account.name)
                prefs().edit().putBoolean(KEY_ENABLED, true)
                    .putInt(KEY_SOURCE, RecoveryMetadata.KEY_SOURCE_RECOVERY_CODE).apply()
                _state.value = Phase.IDLE
                onDone(Result.BackupReady(replaced))
            } catch (e: CancellationException) {
                _state.value = Phase.IDLE
                throw e
            } catch (e: Exception) {
                _state.value = Phase.IDLE
                onDone(Result.Failed(reasonOf(e)))
            }
        }
    }

    // ── restore (new device) ────────────────────────────────────────────

    /**
     * Downloads blob + sidecar from the chosen Google account, then runs
     * the passkey ceremony and hands the blob+secret to the core.
     * The daemon must be running in recovery mode ([service] unprovisioned).
     */
    fun beginRestore(activity: Activity, account: Account, service: StilleService?, onDone: (Result) -> Unit) {
        if (service == null) {
            onDone(Result.Failed(Reason.CORE_UNAVAILABLE))
            return
        }
        scope.launch {
            var phase = Phase.DOWNLOADING
            try {
                _state.value = phase
                val downloaded = withContext(Dispatchers.IO) { store(account, activity).download() }
                    ?: run { _state.value = Phase.IDLE; onDone(Result.Failed(Reason.NO_BACKUP)); return@launch }
                val metadata = decodeSidecar(downloaded.sidecar)
                    ?: run { _state.value = Phase.IDLE; onDone(Result.Failed(Reason.NO_BACKUP)); return@launch }
                pending = downloaded.blob to metadata
                if (metadata.keySource == RecoveryMetadata.KEY_SOURCE_PASSKEY) {
                    phase = Phase.DERIVING_SECRET
                    _state.value = phase
                    val secret = passkeys.deriveUnlockSecret(activity, metadata.prfSalt!!)
                    phase = Phase.RESTORING
                    _state.value = phase
                    val name = restore(service, downloaded.blob, secret)
                    _state.value = Phase.IDLE
                    onDone(Result.Restored(name))
                } else {
                    _state.value = Phase.IDLE
                    onDone(Result.NeedsRecoveryCode)
                }
            } catch (e: CancellationException) {
                _state.value = Phase.IDLE
                throw e
            } catch (e: Exception) {
                _state.value = Phase.IDLE
                onDone(Result.Failed(reasonOf(e)))
            }
        }
    }

    /** Typed-in recovery code path after [Result.NeedsRecoveryCode]. */
    fun restoreWithRecoveryCode(service: StilleService?, code: ByteArray, onDone: (Result) -> Unit) {
        val pair = pending
        if (service == null || pair == null) {
            onDone(Result.Failed(if (service == null) Reason.CORE_UNAVAILABLE else Reason.NO_BACKUP))
            return
        }
        scope.launch {
            try {
                _state.value = Phase.RESTORING
                val name = restore(service, pair.first, code)
                _state.value = Phase.IDLE
                onDone(Result.Restored(name))
            } catch (e: CancellationException) {
                _state.value = Phase.IDLE
                throw e
            } catch (e: Exception) {
                _state.value = Phase.IDLE
                onDone(Result.Failed(reasonOf(e)))
            }
        }
    }

    // ── delete ──────────────────────────────────────────────────────────

    fun deleteBackup(activity: Activity, account: Account, onDone: (Result) -> Unit) {
        scope.launch {
            try {
                _state.value = Phase.DELETING
                withContext(Dispatchers.IO) { store(account, activity).delete() }
                AccountManagerDriveTokens.clearAccountName(context)
                prefs().edit().putBoolean(KEY_ENABLED, false).remove(KEY_SOURCE).apply()
                _state.value = Phase.IDLE
                onDone(Result.Done)
            } catch (e: CancellationException) {
                _state.value = Phase.IDLE
                throw e
            } catch (e: Exception) {
                _state.value = Phase.IDLE
                onDone(Result.Failed(reasonOf(e)))
            }
        }
    }

    // ── internals ───────────────────────────────────────────────────────

    private suspend fun seal(service: StilleService, secret: ByteArray): ByteArray =
        suspendCancellableCoroutine { continuation ->
            service.createRecoveryBackup(secret) { result ->
                when (result) {
                    is StilleService.Result.BackupCreated -> continuation.resume(result.blob)
                    is StilleService.Result.Error ->
                        continuation.resumeWithException(CoreRecoveryException(result.error.toReason()))
                    else -> {}
                }
            }
        }

    private suspend fun restore(service: StilleService, blob: ByteArray, secret: ByteArray): String {
        val name = suspendCancellableCoroutine<String> { continuation ->
            service.restoreRecoveryBackup(blob, secret) { result ->
                when (result) {
                    is StilleService.Result.Restored -> continuation.resume(result.profile.name)
                    is StilleService.Result.Error ->
                        continuation.resumeWithException(CoreRecoveryException(result.error.toReason()))
                    else -> {}
                }
            }
        }
        pending = null
        return name
    }

    private suspend fun upload(account: Account, blob: ByteArray, metadata: RecoveryMetadata.Metadata, activity: Activity): Boolean =
        withContext(Dispatchers.IO) {
            store(account, activity).upload(blob, RecoveryMetadata.encode(metadata))
        }

    private fun store(account: Account, activity: Activity): DriveBackupStore =
        DriveBackupStore(UrlConnectionTransport(), AccountManagerDriveTokens(context, account, activity))

    private fun decodeSidecar(bytes: ByteArray?): RecoveryMetadata.Metadata? {
        if (bytes == null) return null
        return try { RecoveryMetadata.decode(bytes) } catch (_: Exception) { null }
    }

    private fun reasonOf(e: Exception): Reason = when (e) {
        is CoreRecoveryException -> e.reason
        is PasskeyBackupKeys.PasskeyException ->
            if (e.cancelled) Reason.CANCELLED else Reason.PASSKEY_UNAVAILABLE
        is DriveBackupStore.StoreException, is java.io.IOException -> Reason.NETWORK
        is AccountManagerDriveTokens.TokenException -> Reason.AUTH
        is RecoveryMetadata.MetadataException -> Reason.NO_BACKUP
        else -> Reason.ERROR
    }

    private fun StilleService.RecoveryError.toReason(): Reason = when (this) {
        StilleService.RecoveryError.CORE_UNAVAILABLE -> Reason.CORE_UNAVAILABLE
        StilleService.RecoveryError.NOT_INITIALIZED -> Reason.NOT_INITIALIZED
        StilleService.RecoveryError.ALREADY_PROVISIONED -> Reason.ALREADY_PROVISIONED
        StilleService.RecoveryError.BAD_RECOVERY -> Reason.WRONG_SECRET
        StilleService.RecoveryError.TOO_BIG -> Reason.TOO_BIG
        else -> Reason.ERROR
    }

    private companion object {
        const val PREFS = "stille_backup"
        const val KEY_ENABLED = "enabled"
        const val KEY_SOURCE = "key_source"
    }
}
