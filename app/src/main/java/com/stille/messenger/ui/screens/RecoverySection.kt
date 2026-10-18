package com.stille.messenger.ui.screens

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stille.messenger.backup.AccountManagerDriveTokens
import com.stille.messenger.backup.RecoveryCode
import com.stille.messenger.backup.RecoveryController
import com.stille.messenger.service.StilleService
import com.stille.messenger.ui.components.GlassSurface
import com.stille.messenger.ui.collectStarted

/** Shared bilingual strings for the recovery flow. */
internal class RecoveryStrings(val english: Boolean) {
    val title get() = if (english) "Account recovery" else "Восстановление аккаунта"
    val enabled get() = if (english) "Saved to Google Drive. Message history is not included." else "Копия сохранена в Google Диске. Без истории сообщений."
    val disabled get() = if (english) "Save your account to restore it on a new phone." else "Сохраните аккаунт, чтобы вернуть его на новом телефоне."
    val enable get() = if (english) "Enable recovery" else "Включить восстановление"
    val update get() = if (english) "Update copy" else "Обновить копию"
    val delete get() = if (english) "Delete copy" else "Удалить копию"
    val success get() = if (english) "Recovery enabled" else "Восстановление включено"
    val successReplaced get() = if (english) "Copy updated" else "Копия обновлена"
    val deleted get() = if (english) "Copy deleted" else "Копия удалена"
    val codeTitle get() = if (english) "Recovery code" else "Код восстановления"
    val codeBody get() = if (english)
        "Save this code somewhere safe. It is shown only once and is needed to restore your account. Do not share it."
        else "Сохраните код в надёжном месте. Он показывается один раз и нужен для восстановления аккаунта. Никому его не передавайте."
    val codeSaved get() = if (english) "I saved the code" else "Код сохранён"
    val cancel get() = if (english) "Cancel" else "Отмена"
    fun error(reason: RecoveryController.Reason) = when (reason) {
        RecoveryController.Reason.NO_GOOGLE_ACCOUNT -> if (english) "No Google account on this device" else "На устройстве нет аккаунта Google"
        RecoveryController.Reason.AUTH -> if (english) "Allow Google Drive access and try again." else "Разрешите доступ к Google Диску и повторите."
        RecoveryController.Reason.NETWORK -> if (english) "Could not reach Google Drive. Check your connection." else "Нет связи с Google Диском. Проверьте интернет."
        RecoveryController.Reason.NO_BACKUP -> if (english) "No saved copy in this Google account." else "В этом аккаунте Google нет сохранённой копии."
        RecoveryController.Reason.WRONG_SECRET -> if (english) "The code does not match, or the copy is damaged." else "Код не подходит или копия повреждена."
        RecoveryController.Reason.NOT_INITIALIZED -> if (english) "Set up your profile first." else "Сначала создайте профиль."
        RecoveryController.Reason.ALREADY_PROVISIONED -> if (english) "This phone already has an account." else "На этом телефоне уже есть аккаунт."
        RecoveryController.Reason.CORE_UNAVAILABLE -> if (english) "App is not ready. Wait a moment and try again." else "Приложение ещё не готово. Подождите и повторите."
        RecoveryController.Reason.CANCELLED -> if (english) "Confirmation cancelled." else "Подтверждение отменено."
        RecoveryController.Reason.PASSKEY_UNAVAILABLE -> if (english) "Sign-in key unavailable. Check your password manager." else "Ключ входа недоступен. Проверьте менеджер паролей."
        RecoveryController.Reason.TOO_BIG -> if (english) "The saved copy is too large." else "Копия слишком большая."
        RecoveryController.Reason.ERROR -> if (english) "Could not save or restore your account. Try again." else "Не удалось сохранить или восстановить аккаунт. Повторите попытку."
    }
}

/** Google-account picker result via the platform chooser (no permissions). */
@Composable
internal fun rememberAccountPicker(onMissing: () -> Unit = {}, onPicked: (Account) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@rememberLauncherForActivityResult
            val name = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            val type = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
            if (name != null && type != null) onPicked(Account(name, type))
        } else onMissing()
    }
    return {
        val intent = AccountManager.newChooseAccountIntent(
            null, null, arrayOf(AccountManagerDriveTokens.GOOGLE_ACCOUNT_TYPE), null, null, null, null
        )
        launcher.launch(intent)
    }
}

/**
 * Settings section for account recovery: passkey + core-sealed identity
 * backup in Google Drive (no message history).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RecoverySection(
    activity: Activity,
    controller: RecoveryController,
    service: StilleService?,
    language: String
) {
    val english = language == "en"
    val strings = remember(english) { RecoveryStrings(english) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val phase by controller.state.collectStarted()
    val busy = phase != RecoveryController.Phase.IDLE
    val coreReady = service?.inviteState?.collectStarted()?.value?.ready == true
    var codePrompt by remember { mutableStateOf<ByteArray?>(null) }
    var pendingAccount by remember { mutableStateOf<Account?>(null) }

    fun handle(result: RecoveryController.Result) {
        when (result) {
            is RecoveryController.Result.BackupReady ->
                message = if (result.replaced) strings.successReplaced else strings.success
            is RecoveryController.Result.RecoveryCode -> codePrompt = result.code
            is RecoveryController.Result.Done -> message = strings.deleted
            is RecoveryController.Result.Failed -> message = strings.error(result.reason)
            else -> {}
        }
    }

    val pickAccount = rememberAccountPicker { account ->
        pendingAccount = account
        controller.enable(activity, account, service) { handle(it) }
    }

    GlassSurface(Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(strings.title, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                if (controller.enabled) strings.enabled else strings.disabled,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!coreReady) {
                Spacer(Modifier.height(8.dp))
                Text(strings.error(RecoveryController.Reason.CORE_UNAVAILABLE),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.layout.FlowRow(verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                if (busy) {
                    CircularProgressIndicator(Modifier.width(28.dp).height(28.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                }
                if (!controller.enabled) {
                    TextButton(onClick = pickAccount, enabled = !busy && coreReady) {
                        Icon(Icons.Outlined.CloudUpload, null, Modifier.padding(end = 8.dp))
                        Text(strings.enable)
                    }
                } else {
                    TextButton(onClick = pickAccount, enabled = !busy && coreReady) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.padding(end = 8.dp))
                        Text(strings.update)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(enabled = !busy && coreReady, onClick = {
                        val account = AccountManagerDriveTokens.savedAccount(activity)
                        if (account == null) {
                            message = strings.error(RecoveryController.Reason.NO_GOOGLE_ACCOUNT)
                        } else {
                            controller.deleteBackup(activity, account) { handle(it) }
                        }
                    }) {
                        Icon(Icons.Outlined.DeleteOutline, null, Modifier.padding(end = 8.dp))
                        Text(strings.delete)
                    }
                }
            }
            message?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    codePrompt?.let { code ->
        AlertDialog(
            onDismissRequest = { codePrompt = null },
            title = { Text(strings.codeTitle) },
            text = {
                Column {
                    Text(strings.codeBody, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    SelectionContainer {
                        Text(
                            RecoveryCode.render(code),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val account = pendingAccount
                    codePrompt = null
                    if (account != null) {
                        controller.enableWithRecoveryCode(activity, account, service, code) { handle(it) }
                    }
                }) { Text(strings.codeSaved) }
            },
            dismissButton = {
                TextButton(onClick = { codePrompt = null }) { Text(strings.cancel) }
            }
        )
    }
}
