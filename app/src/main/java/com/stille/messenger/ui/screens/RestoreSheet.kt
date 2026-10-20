package com.stille.messenger.ui.screens

import android.accounts.Account
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stille.messenger.backup.BackupTarget
import com.stille.messenger.backup.RecoveryCode
import com.stille.messenger.backup.RecoveryController
import com.stille.messenger.backup.WebDavBackupStore
import com.stille.messenger.service.StilleService
import com.stille.messenger.ui.collectStarted

/**
 * New-device restore flow (CORE-API §4.10, CLIENT_RULES §4.4):
 * ```
 * выбрать хранилище (Google Диск / Яндекс Диск / WebDAV) →
 * подтвердить passkey / ввести код → RESTORE_RECOVERY_BACKUP →
 * тот же аккаунт, пустая история
 * ```
 * The daemon must already be running in unprovisioned recovery mode
 * ([StilleService.startRecovery]); this dialog waits for its readiness.
 */
@Composable
fun RestoreBackupDialog(
    activity: Activity,
    controller: RecoveryController,
    service: StilleService?,
    language: String,
    onRestored: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    val english = language == "en"
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var codeInput by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var chooseProvider by rememberSaveable { mutableStateOf(false) }
    var webDavForm by rememberSaveable { mutableStateOf<String?>(null) } // preset url
    var consentRetry by remember { mutableStateOf<(() -> Unit)?>(null) }

    // The daemon boots unprovisioned; wait for the UDS API to come up.
    val coreReady = service?.inviteState?.collectStarted()?.value?.ready == true

    val strings = remember(english) { RecoveryStrings(english) }
    fun errorText(reason: RecoveryController.Reason): String = strings.error(reason)

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val retry = consentRetry
        if (result.resultCode == Activity.RESULT_OK && retry != null) {
            retry()
        } else {
            busy = false
            error = errorText(RecoveryController.Reason.AUTH)
        }
    }

    fun beginRestore(target: BackupTarget) {
        busy = true
        error = null
        controller.beginRestore(activity, target, service) { result ->
            when (result) {
                is RecoveryController.Result.Restored -> onRestored(result.name)
                is RecoveryController.Result.NeedsRecoveryCode -> { busy = false; codeInput = true }
                is RecoveryController.Result.ConsentRequired -> {
                    consentRetry = { controller.beginRestore(activity, target, service) { inner ->
                        when (inner) {
                            is RecoveryController.Result.Restored -> onRestored(inner.name)
                            is RecoveryController.Result.NeedsRecoveryCode -> { busy = false; codeInput = true }
                            is RecoveryController.Result.Failed -> { busy = false; error = errorText(inner.reason) }
                            else -> {}
                        }
                    } }
                    consentLauncher.launch(result.intent)
                }
                is RecoveryController.Result.Failed -> { busy = false; error = errorText(result.reason) }
                else -> {}
            }
        }
    }

    val pickAccount = rememberAccountPicker { account: Account ->
        beginRestore(BackupTarget.GoogleDrive(account))
    }

    fun openProviderChoice() {
        if (webDavForm == null && !codeInput) chooseProvider = true
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (english) "Restore account" else "Восстановить аккаунт") },
        text = {
            Column {
                Text(
                    if (english)
                        "Choose the storage with your saved copy — Google Drive, Yandex Disk or another WebDAV cloud. Confirm your sign-in key or enter your recovery code. Your profile and contacts will return, but not your messages."
                    else
                        "Выберите хранилище с сохранённой копией — Google Диск, Яндекс Диск или другое WebDAV-облако. Подтвердите ключ входа или введите код восстановления. Профиль и контакты вернутся, переписка — нет.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                }
                if (codeInput) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { value ->
                            code = value.filter { it.isLetterOrDigit() || it.isWhitespace() || it == '-' }
                                .take(71) // 64 hex + 7 group separators
                        },
                        label = { Text(if (english) "Recovery code" else "Код восстановления") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (!codeInput) {
                if (busy || !coreReady) {
                    CircularProgressIndicator(Modifier.height(24.dp).padding(end = 16.dp), strokeWidth = 2.dp)
                } else {
                    OutlinedButton(onClick = { openProviderChoice() }) {
                        Text(if (english) "Choose storage" else "Выбрать хранилище")
                    }
                }
            } else {
                TextButton(
                    onClick = {
                        val parsed = RecoveryCode.parse(code)
                        if (parsed != null) {
                            busy = true
                            code = ""
                            controller.restoreWithRecoveryCode(service, parsed) { result ->
                                busy = false
                                when (result) {
                                    is RecoveryController.Result.Restored -> onRestored(result.name)
                                    is RecoveryController.Result.Failed -> error = errorText(result.reason)
                                    else -> {}
                                }
                            }
                        } else {
                            error = if (english) "Invalid recovery code format" else "Неверный формат кода"
                        }
                    },
                    enabled = !busy && RecoveryCode.parse(code) != null
                ) { Text(if (english) "Restore" else "Восстановить") }
            }
        },
        dismissButton = {
            if (!busy) TextButton(onClick = onDismiss) { Text(if (english) "Cancel" else "Отмена") }
        }
    )

    if (chooseProvider) {
        AlertDialog(
            onDismissRequest = { chooseProvider = false },
            title = { Text(strings.storageTitle) },
            text = { Text(strings.storageBody, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                Column(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { chooseProvider = false; pickAccount() }) {
                        Text(strings.googleDrive)
                    }
                    TextButton(onClick = { chooseProvider = false; webDavForm = WebDavBackupStore.YANDEX_DISK_URL }) {
                        Text(strings.yandexDisk)
                    }
                    TextButton(onClick = { chooseProvider = false; webDavForm = "" }) {
                        Text(strings.otherWebDav)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { chooseProvider = false }) { Text(strings.cancel) }
            }
        )
    }

    webDavForm?.let { preset ->
        WebDavLoginDialog(
            english = english,
            presetUrl = preset,
            confirmLabel = strings.webdavConnect,
            onDismiss = { webDavForm = null },
            onSubmit = { credentials ->
                webDavForm = null
                beginRestore(BackupTarget.WebDav(credentials))
            }
        )
    }
}
