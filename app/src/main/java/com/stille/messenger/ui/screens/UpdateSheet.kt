package com.stille.messenger.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stille.messenger.update.DownloadState
import com.stille.messenger.update.ReleaseInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    release: ReleaseInfo,
    english: Boolean,
    downloadState: DownloadState = DownloadState.Idle,
    onUpdate: () -> Unit,
    onCancelDownload: () -> Unit = {},
    onLater: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onLater,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(if (english) "A new Stille update is available" else "Доступно новое обновление Stille", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(if (english) "Version ${release.version} is ready to download from GitHub Releases." else "Версия ${release.version} доступна для скачивания из GitHub Releases.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            when (downloadState) {
                is DownloadState.Running -> {
                    val progress = downloadState.received.toFloat() / (downloadState.total ?: 1L).toFloat()
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (english) "Downloading… ${downloadState.received / 1024} / ${downloadState.total?.let { it / 1024 } ?: '?'} KiB"
                        else "Загрузка… ${downloadState.received / 1024} / ${downloadState.total?.let { it / 1024 } ?: '?'} КиБ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(if (english) "Cancel" else "Отменить")
                    }
                }
                is DownloadState.Error -> {
                    Text(
                        if (english) "Update failed: ${downloadState.message}" else "Не удалось обновить: ${downloadState.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) { Text(if (english) "Retry" else "Повторить") }
                }
                else -> {
                    Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) {
                        if (downloadState is DownloadState.Ready) {
                            Text(if (english) "Install update" else "Установить обновление")
                        } else {
                            Text(if (english) "Update" else "Обновить")
                        }
                    }
                }
            }
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) { Text(if (english) "Later" else "Позже") }
        }
    }
}
