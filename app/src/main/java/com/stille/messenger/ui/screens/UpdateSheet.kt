package com.stille.messenger.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stille.messenger.update.ReleaseInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(release: ReleaseInfo, english: Boolean, onUpdate: () -> Unit, onLater: () -> Unit) {
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
            Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) { Text(if (english) "Update" else "Обновить") }
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) { Text(if (english) "Later" else "Позже") }
        }
    }
}
