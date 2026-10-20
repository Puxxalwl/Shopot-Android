package com.stille.messenger.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.stille.messenger.diag.StilleLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREVIEW_LINES = 80

/**
 * Diagnostics entry unlocked by five rapid taps on "About Stille".
 *
 * Shows the tail of the redacted log file, its size, and offers share /
 * clear actions. The file itself lives in the app's private storage and is
 * exposed read-only through the `.logs` FileProvider for sharing.
 */
@Composable
fun DiagnosticsDialog(english: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        preview = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(StilleLog.directory(context), StilleLog.FILE_NAME)
                if (!file.isFile) return@runCatching null
                file.readLines().takeLast(PREVIEW_LINES).joinToString("\n")
            }.getOrNull()
        }
    }

    fun share() {
        runCatching {
            val file = File(StilleLog.directory(context), StilleLog.FILE_NAME)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Stille diagnostics")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, if (english) "Share diagnostics log" else "Поделиться логом"))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (english) "Diagnostics" else "Диагностика") },
        text = {
            Column {
                Text(
                    if (english)
                        "A redacted technical log. It contains no messages, contacts or secrets — only stages and error types."
                    else
                        "Технический лог без личных данных: только этапы работы и типы ошибок — без сообщений, контактов и секретов.",
                    style = MaterialTheme.typography.bodySmall
                )
                val bytes = StilleLog.totalBytes()
                if (bytes > 0) {
                    val modified = StilleLog.lastModified()
                        .takeIf { it > 0 }
                        ?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it)) }
                    Text(
                        (if (english) "Size" else "Размер") + ": ${bytes / 1024} KiB" +
                            (modified?.let { " · " + (if (english) "updated" else "обновлён") + " $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                preview?.let { text ->
                    androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                    Text(
                        text,
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { share() }) { Text(if (english) "Share" else "Поделиться") }
        },
        dismissButton = {
            TextButton(onClick = {
                StilleLog.clear()
                preview = null
            }) { Text(if (english) "Clear" else "Очистить") }
            TextButton(onClick = onDismiss) { Text(if (english) "Close" else "Закрыть") }
        }
    )
}
