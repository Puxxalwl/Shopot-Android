package com.stille.messenger.ui.qr

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Memory-only QR preview. Encoding, verification and bitmap allocation all run on
 * Dispatchers.Default. A changed invite immediately discards the previous preview;
 * cancellation cannot publish the previous result. No rememberSaveable or cache.
 *
 * Allow at least (moduleCount + 8) physical pixels per side; more is preferable.
 * Rendering uses integer physical pixels per module, no interpolation, and white
 * letterboxing. Modifier clipping/padding outside this component must not obscure
 * the code. [contentDescription] should describe the action, NOT contain the invite.
 */
@Composable
fun InviteQrCode(
    rawInvite: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "Invite QR code",
    errorText: String = "Unable to display invite QR code",
) {
    // A fresh state holder prevents one frame of the old QR after a key change.
    key(rawInvite) {
        val state by produceState<QrPreviewState>(QrPreviewState.Loading, rawInvite) {
            value = withContext(Dispatchers.Default) {
                try {
                    val qr = InviteQrRenderer.render(rawInvite)
                    ensureActive()
                    val bitmap = Bitmap.createBitmap(
                        qr.copyArgbPixels(), qr.size, qr.size, Bitmap.Config.ARGB_8888,
                    ).asImageBitmap()
                    ensureActive()
                    QrPreviewState.Ready(bitmap, qr.moduleCount + 2 * qr.quietZoneModules)
                } catch (_: InviteQrException) {
                    QrPreviewState.Failed
                }
            }
        }
        Box(
            modifier = modifier.size(280.dp).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            when (val current = state) {
                QrPreviewState.Loading -> CircularProgressIndicator(color = Color.Black)
                QrPreviewState.Failed -> Text(errorText, color = Color.Black)
                is QrPreviewState.Ready -> Canvas(
                    Modifier.matchParentSize().semantics {
                        this.contentDescription = contentDescription
                    },
                ) {
                    val scale = min(size.width, size.height).toInt() / current.totalModules
                    // Never compress modules below one physical pixel.
                    if (scale > 0) {
                        val side = scale * current.totalModules
                        drawImage(
                            image = current.bitmap,
                            dstOffset = IntOffset(
                                (size.width.toInt() - side) / 2,
                                (size.height.toInt() - side) / 2,
                            ),
                            dstSize = IntSize(side, side),
                            filterQuality = FilterQuality.None,
                        )
                    }
                }
            }
        }
    }
}

private sealed interface QrPreviewState {
    data object Loading : QrPreviewState
    data object Failed : QrPreviewState
    class Ready(val bitmap: ImageBitmap, val totalModules: Int) : QrPreviewState
}
