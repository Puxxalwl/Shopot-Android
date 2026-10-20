package com.stille.messenger.ui.screens

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.stille.messenger.backup.AccountManagerDriveTokens
import com.stille.messenger.backup.BackupTarget
import com.stille.messenger.backup.RecoveryCode
import com.stille.messenger.backup.RecoveryController
import com.stille.messenger.backup.WebDavBackupStore
import com.stille.messenger.backup.WebDavCredentials
import com.stille.messenger.service.StilleService
import com.stille.messenger.ui.components.GlassSurface
import com.stille.messenger.ui.collectStarted

/** Shared bilingual strings for the recovery flow. */
internal class RecoveryStrings(val english: Boolean) {
    val title get() = if (english) "Account recovery" else "Восстановление аккаунта"
    val enabled get() = if (english) "Saved to your cloud storage. Message history is not included." else "Копия сохранена в вашем облачном хранилище. Без истории сообщений."
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
    val storageTitle get() = if (english) "Where to keep the copy" else "Где хранить копию"
    val storageBody get() = if (english) "The encrypted copy is stored in your own cloud. Stille has no servers." else "Зашифрованная копия хранится в вашем облаке. У Stille нет своих серверов."
    val googleDrive get() = if (english) "Google Drive" else "Google Диск"
    val yandexDisk get() = if (english) "Yandex Disk" else "Яндекс Диск"
    val otherWebDav get() = if (english) "Other WebDAV (Nextcloud, …)" else "Другой WebDAV (Nextcloud и др.)"
    val webdavUrl get() = if (english) "Server address" else "Адрес сервера"
    val webdavLogin get() = if (english) "Username" else "Логин"
    val webdavPassword get() = if (english) "Password" else "Пароль"
    val webdavYandexHint get() = if (english)
        "Yandex requires an app password for external apps: create one at id.yandex.ru → Security."
        else "Для внешних приложений Яндекс использует пароль приложения: создайте его на id.yandex.ru → Безопасность."
    val webdavSave get() = if (english) "Save copy" else "Сохранить копию"
    val webdavConnect get() = if (english) "Connect" else "Подключиться"
    val webdavInvalid get() = if (english) "Enter the server address, username and password." else "Введите адрес сервера, логин и пароль."
    fun error(reason: RecoveryController.Reason) = when (reason) {
        RecoveryController.Reason.NO_GOOGLE_ACCOUNT -> if (english) "No Google account on this device" else "На устройстве нет аккаунта Google"
        RecoveryController.Reason.AUTH -> if (english) "Allow access to the storage and try again. Check the login and password." else "Разрешите доступ к хранилищу и повторите. Проверьте логин и пароль."
        RecoveryController.Reason.NETWORK -> if (english) "Could not reach the storage. Check your connection." else "Нет связи с хранилищем. Проверьте интернет."
        RecoveryController.Reason.NO_BACKUP -> if (english) "No saved copy in this storage." else "В этом хранилище нет сохранённой копии."
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
 * Login form for WebDAV providers. [presetUrl] fills the Yandex Disk address.
 */
@Composable
internal fun WebDavLoginDialog(
    english: Boolean,
    presetUrl: String = "",
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (WebDavCredentials) -> Unit,
) {
    var url by rememberSaveable(presetUrl) { mutableStateOf(presetUrl) }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (english) "Cloud storage" else "Облачное хранилище") },
        text = {
            Column {
                if (presetUrl == WebDavBackupStore.YANDEX_DISK_URL) {
                    Text(RecoveryStrings(english).webdavYandexHint, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = false },
                    label = { Text(RecoveryStrings(english).webdavUrl) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrect = false),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it; error = false },
                    label = { Text(RecoveryStrings(english).webdavLogin) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = false },
                    label = { Text(RecoveryStrings(english).webdavPassword) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Spacer(Modifier.height(8.dp))
                    Text(RecoveryStrings(english).webdavInvalid, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val credentials = runCatching {
                    WebDavCredentials(url, user, password).validated()
                }.getOrNull()
                if (credentials == null) error = true else onSubmit(credentials)
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(RecoveryStrings(english).cancel) }
        }
    )
}

/**
 * Settings section for account recovery: passkey + core-sealed identity
 * backup in the user's own cloud — Google Drive, Yandex Disk or any WebDAV
 * server (no message history).
 */
@OptIn(ExperimentalLayoutApi::class)
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
    var pendingTarget by remember { mutableStateOf<BackupTarget?>(null) }
    var chooseProvider by remember { mutableStateOf(false) }
    var webDavForm by remember { mutableStateOf<String?>(null) } // preset url
    // Retried after the user completes Google's interactive consent screen.
    var consentRetry by remember { mutableStateOf<(() -> Unit)?>(null) }

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

    // Google Drive consent: launch Google's screen, then retry the operation.
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val retry = consentRetry
        if (result.resultCode == Activity.RESULT_OK && retry != null) {
            retry()
        } else {
            message = strings.error(RecoveryController.Reason.AUTH)
        }
    }

    fun withConsentRetry(retry: () -> Unit, intent: android.content.Intent) {
        consentRetry = retry
        consentLauncher.launch(intent)
    }

    fun startEnable(target: BackupTarget) {
        pendingTarget = target
        controller.enable(activity, target, service) { result ->
            if (result is RecoveryController.Result.ConsentRequired) {
                withConsentRetry({ controller.enable(activity, target, service) { handle(it) } }, result.intent)
            } else handle(result)
        }
    }

    fun startDelete(target: BackupTarget) {
        controller.deleteBackup(activity, target) { result ->
            if (result is RecoveryController.Result.ConsentRequired) {
                withConsentRetry({ controller.deleteBackup(activity, target) { handle(it) } }, result.intent)
            } else handle(result)
        }
    }

    val pickAccount = rememberAccountPicker { account ->
        startEnable(BackupTarget.GoogleDrive(account))
    }

    fun refreshCopy() {
        when (val saved = controller.savedTarget()) {
            null -> chooseProvider = true
            else -> startEnable(saved)
        }
    }

    fun deleteCopy() {
        when (val saved = controller.savedTarget()) {
            null -> message = strings.error(RecoveryController.Reason.NO_GOOGLE_ACCOUNT)
            else -> startDelete(saved)
        }
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
            FlowRow(verticalArrangement = Arrangement.Center) {
                if (busy) {
                    CircularProgressIndicator(Modifier.width(28.dp).height(28.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                }
                if (!controller.enabled) {
                    TextButton(onClick = { chooseProvider = true }, enabled = !busy && coreReady) {
                        Icon(Icons.Outlined.CloudUpload, null, Modifier.padding(end = 8.dp))
                        Text(strings.enable)
                    }
                } else {
                    TextButton(onClick = { refreshCopy() }, enabled = !busy && coreReady) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.padding(end = 8.dp))
                        Text(strings.update)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(enabled = !busy && coreReady, onClick = { deleteCopy() }) {
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

    if (chooseProvider) {
        AlertDialog(
            onDismissRequest = { chooseProvider = false },
            title = { Text(strings.storageTitle) },
            text = { Text(strings.storageBody, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { chooseProvider = false; pickAccount() }) {
                        Icon(Icons.Outlined.Language, null, Modifier.padding(end = 8.dp))
                        Text(strings.googleDrive)
                    }
                    TextButton(onClick = { chooseProvider = false; webDavForm = WebDavBackupStore.YANDEX_DISK_URL }) {
                        Text(strings.yandexDisk)
                    }
                    TextButton(onClick = { chooseProvider = false; webDavForm = "" }) {
                        Icon(Icons.Outlined.Dns, null, Modifier.padding(end = 8.dp))
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
            confirmLabel = strings.webdavSave,
            onDismiss = { webDavForm = null },
            onSubmit = { credentials ->
                webDavForm = null
                startEnable(BackupTarget.WebDav(credentials))
            }
        )
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
                    val target = pendingTarget
                    codePrompt = null
                    if (target != null) {
                        controller.enableWithRecoveryCode(activity, target, service, code) { handle(it) }
                    }
                }) { Text(strings.codeSaved) }
            },
            dismissButton = {
                TextButton(onClick = { codePrompt = null }) { Text(strings.cancel) }
            }
        )
    }
}
