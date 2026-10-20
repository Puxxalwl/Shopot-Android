package com.stille.messenger.ui.qr

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.Camera
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.stille.messenger.diag.StilleLog
import com.stille.messenger.service.INVITE_PREFIXES
import com.stille.messenger.service.validInviteTarget
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen, memory-only QR scanner for Stille invitations.
 *
 * The scanner owns a legacy [Camera] while this dialog is visible. It uses one
 * NV21 callback buffer and a single bounded decoder worker; camera frames never
 * reach the UI thread or a disk-backed cache. Only values matching the Stille
 * invitation shape are returned through [onScanned].
 *
 * The host must declare CAMERA in its manifest. [onPhotoImport] is intentionally
 * only a UI hook: the host owns image picking and decoding integration.
 */
@Composable
fun LiveQrScanner(
    onScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    language: String? = null,
    onPhotoImport: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val english = (language ?: com.stille.messenger.ui.theme.StilleColors.language) == "en"
    val currentOnScanned = rememberUpdatedState(onScanned)
    val hasCamera = remember(context) { hasCamera(context) }
    var permissionResult by remember { mutableStateOf<Boolean?>(null) }
    var cameraError by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<LiveQrCameraView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionResult = granted }

    // Coming back from the app-details screen: pick up a granted permission.
    val detailsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            permissionResult = true
        }
    }

    fun openApplicationSettings() {
        detailsLauncher.launch(
            android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null),
            )
        )
    }

    val permissionGranted = permissionResult
        ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    val permanentlyDenied = permissionResult == false && context is Activity &&
        !ActivityCompat.shouldShowRequestPermissionRationale(context, Manifest.permission.CAMERA)

    // Ask for the camera as soon as the scanner opens instead of showing a
    // black screen and waiting for the user to find the "allow" button.
    LaunchedEffect(hasCamera, permissionResult) {
        if (hasCamera && permissionResult == null &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !hasCamera -> ScannerMessage(
                    title = if (english) "No camera available" else "Камера недоступна",
                    body = if (english) "This device does not have a usable camera." else "На этом устройстве нет доступной камеры.",
                    onClose = onClose,
                    english = english,
                )
                !permissionGranted -> PermissionMessage(
                    permanentlyDenied = permanentlyDenied,
                    onRequest = {
                        if (!permanentlyDenied) permissionLauncher.launch(Manifest.permission.CAMERA)
                        else openApplicationSettings()
                    },
                    onOpenSettings = ::openApplicationSettings,
                    onClose = onClose,
                    english = english,
                )
                cameraError -> ScannerMessage(
                    title = if (english) "Camera unavailable" else "Камера недоступна",
                    body = if (english) "Close other apps using the camera, then try again." else "Закройте другие приложения, использующие камеру, и попробуйте снова.",
                    onRetry = {
                        cameraError = false
                        previewView?.restartCamera()
                    },
                    onClose = onClose,
                    english = english,
                )
                else -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            LiveQrCameraView(
                                context = viewContext,
                                onDecoded = { currentOnScanned.value(it) },
                                onError = { cameraError = true },
                            ).also { previewView = it }
                        },
                        update = { previewView = it },
                    )
                    ScannerOverlay(
                        english = english,
                        onClose = onClose,
                        onPhotoImport = onPhotoImport,
                    )
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val view = previewView
        if (view == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> view.resumeCamera()
                    Lifecycle.Event.ON_PAUSE -> view.pauseCamera()
                    Lifecycle.Event.ON_DESTROY -> view.dispose()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                view.resumeCamera()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                view.dispose()
                previewView = null
            }
        }
    }
}

@Composable
private fun ScannerOverlay(
    english: Boolean,
    onClose: () -> Unit,
    onPhotoImport: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize()) {
        // Dialog windows with decorFitsSystemWindows(false) do not reliably
        // feed Compose the status/navigation bar paddings, so the overlay
        // insets itself from safeDrawing with generous bottom padding as a
        // fallback — the controls can never slide under system bars.
        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (english) "Scan invitation" else "Сканировать приглашение",
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, if (english) "Close" else "Закрыть", tint = Color.White)
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val side = (size.minDimension * 0.72f).coerceAtLeast(180.dp.toPx())
                .coerceAtMost(size.minDimension - 32.dp.toPx())
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f
            val corner = side * 0.13f
            val stroke = 3.dp.toPx()
            val color = Color.White.copy(alpha = 0.96f)
            drawRoundRect(
                color = color.copy(alpha = 0.25f),
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(side, side),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                style = Stroke(stroke),
            )
            // Opaque corner guides remain visible over both light and dark previews.
            drawLine(color, androidx.compose.ui.geometry.Offset(left, top + corner), androidx.compose.ui.geometry.Offset(left, top), stroke)
            drawLine(color, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left + corner, top), stroke)
            drawLine(color, androidx.compose.ui.geometry.Offset(left + side - corner, top), androidx.compose.ui.geometry.Offset(left + side, top), stroke)
            drawLine(color, androidx.compose.ui.geometry.Offset(left + side, top), androidx.compose.ui.geometry.Offset(left + side, top + corner), stroke)
            drawLine(color, androidx.compose.ui.geometry.Offset(left, top + side - corner), androidx.compose.ui.geometry.Offset(left, top + side), stroke)
            drawLine(color, androidx.compose.ui.geometry.Offset(left, top + side), androidx.compose.ui.geometry.Offset(left + corner, top + side), stroke)
            drawLine(color, androidx.compose.ui.geometry.Offset(left + side - corner, top + side), androidx.compose.ui.geometry.Offset(left + side, top + side), stroke)
            drawLine(color, androidx.compose.ui.geometry.Offset(left + side, top + side - corner), androidx.compose.ui.geometry.Offset(left + side, top + side), stroke)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (english) "Place the QR code inside the frame" else "Поместите QR-код в рамку",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (english) "Only Stille invitations are accepted" else "Принимаются только приглашения Stille",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (onPhotoImport != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onPhotoImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (english) "Choose an image" else "Выбрать изображение")
                }
            }
        }
    }
}

@Composable
private fun PermissionMessage(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    english: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (permanentlyDenied) {
                if (english) "Camera permission is off" else "Доступ к камере отключён"
            } else {
                if (english) "Camera permission needed" else "Нужен доступ к камере"
            },
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (permanentlyDenied) {
                if (english) "Allow camera access in Android Settings to scan an invitation." else "Разрешите доступ к камере в настройках Android, чтобы сканировать приглашение."
            } else {
                if (english) "Stille uses the camera only while this scanner is open." else "Stille использует камеру только пока открыт сканер."
            },
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        if (permanentlyDenied) {
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (english) "Open settings" else "Открыть настройки")
            }
            Spacer(Modifier.height(8.dp))
        } else {
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                Text(if (english) "Allow camera" else "Разрешить камеру")
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(if (english) "Close" else "Закрыть") }
    }
}

@Composable
private fun ScannerMessage(
    title: String,
    body: String,
    onClose: () -> Unit,
    english: Boolean,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(body, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(if (english) "Try again" else "Повторить") }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(if (english) "Close" else "Закрыть") }
    }
}

private class LiveQrCameraView(
    context: Context,
    private val onDecoded: (String) -> Unit,
    private val onError: () -> Unit,
) : SurfaceView(context), SurfaceHolder.Callback {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val disposed = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val opening = AtomicBoolean(false)
    private val callbackEnabled = AtomicBoolean(false)
    private val decodeInFlight = AtomicBoolean(false)
    private val delivered = AtomicBoolean(false)
    private val decoder: ExecutorService = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(1),
        ThreadFactory { runnable -> Thread(runnable, "stille-qr-decoder").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
    // Camera.open() can take hundreds of milliseconds and must never run on
    // the main thread; one dedicated opener serializes that work.
    private val cameraOpener: ExecutorService = Executors.newSingleThreadExecutor(
        ThreadFactory { runnable -> Thread(runnable, "stille-qr-camera").apply { isDaemon = true } },
    )
    private var camera: Camera? = null
    private var previewWidth = 0
    private var previewHeight = 0
    private var surfaceReady = false

    init {
        holder.addCallback(this)
        setBackgroundColor(android.graphics.Color.BLACK)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        if (!paused.get()) startCamera()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (surfaceReady && !paused.get() && camera == null) startCamera()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopCamera()
    }

    fun resumeCamera() {
        if (disposed.get()) return
        paused.set(false)
        if (surfaceReady) startCamera()
    }

    fun pauseCamera() {
        paused.set(true)
        stopCamera()
    }

    fun restartCamera() {
        if (disposed.get()) return
        paused.set(false)
        stopCamera()
        if (surfaceReady) startCamera()
    }

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        paused.set(true)
        stopCamera()
        decoder.shutdownNow()
        cameraOpener.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun startCamera() {
        if (disposed.get() || paused.get() || !surfaceReady || camera != null || !opening.compareAndSet(false, true)) return
        cameraOpener.execute {
            var opened: Camera? = null
            var lastError: Exception? = null
            // Camera.open occasionally fails once right after another app
            // releases the sensor; one quiet retry avoids a dead black screen.
            for (attempt in 0 until OPEN_ATTEMPTS) {
                try {
                    opened = openAndConfigure()
                    lastError = null
                    break
                } catch (error: Exception) {
                    lastError = error
                    try { Thread.sleep(CAMERA_RETRY_DELAY_MS) } catch (_: InterruptedException) { }
                }
            }
            val result = opened
            mainHandler.post {
                if (result == null) {
                    StilleLog.e("qr", "camera-open-failed", lastError)
                    opening.set(false)
                    onErrorOnMain()
                } else {
                    attachPreview(result)
                }
            }
        }
    }

    /** Opens and configures the back camera on the opener thread. */
    private fun openAndConfigure(): Camera {
        val id = backCameraId() ?: throw IllegalStateException("No camera")
        val opened = Camera.open(id)
        try {
            configureCamera(opened, id)
            return opened
        } catch (error: Exception) {
            try { opened.release() } catch (_: RuntimeException) { }
            throw error
        }
    }

    /**
     * Must run on the main thread: binds the opened camera to the surface and
     * starts the preview. Takes ownership of [opened]; releases it on failure.
     */
    private fun attachPreview(opened: Camera) {
        if (disposed.get() || paused.get() || !surfaceReady) {
            opening.set(false)
            try { opened.release() } catch (_: RuntimeException) { }
            return
        }
        try {
            opened.setPreviewCallbackWithBuffer { data, source -> onPreviewFrame(data, source, opened) }
            opened.setPreviewDisplay(holder)
            opened.addCallbackBuffer(ByteArray(previewBufferSize()))
            camera = opened
            callbackEnabled.set(true)
            opening.set(false)
            opened.startPreview()
            StilleLog.i("qr", "camera-preview-started", "size=${previewWidth}x${previewHeight}")
        } catch (error: Exception) {
            opening.set(false)
            callbackEnabled.set(false)
            try { opened.setPreviewCallbackWithBuffer(null) } catch (_: RuntimeException) { }
            try { opened.release() } catch (_: RuntimeException) { }
            StilleLog.e("qr", "camera-attach-failed", error)
            onErrorOnMain()
        }
    }

    private fun configureCamera(value: Camera, cameraId: Int) {
        val parameters = value.parameters
        parameters.previewFormat = ImageFormat.NV21
        val selected = choosePreviewSize(parameters.supportedPreviewSizes)
        parameters.setPreviewSize(selected.width, selected.height)
        value.parameters = parameters
        previewWidth = selected.width
        previewHeight = selected.height
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        value.setDisplayOrientation(displayOrientation(info))
    }

    private fun choosePreviewSize(sizes: List<Camera.Size>): Camera.Size {
        val maxPixels = 1920 * 1080
        val targetRatio = if (height > 0) width.toFloat() / height else 9f / 16f
        return sizes
            .filter { it.width * it.height <= maxPixels }
            .minWithOrNull(compareBy<Camera.Size> { kotlin.math.abs((it.width.toFloat() / it.height) - targetRatio) }.thenByDescending { it.width * it.height })
            ?: sizes.minByOrNull { it.width * it.height }
            ?: throw IllegalStateException("No preview size")
    }

    private fun displayOrientation(info: Camera.CameraInfo): Int {
        val rotation = when (display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (info.orientation + rotation) % 360
        } else {
            (info.orientation - rotation + 360) % 360
        }
    }

    private fun onPreviewFrame(data: ByteArray?, source: Camera, sourceCamera: Camera) {
        if (data == null || source !== sourceCamera || !callbackEnabled.get() || disposed.get()) return
        if (!decodeInFlight.compareAndSet(false, true)) {
            // The single callback buffer is immediately returned when a decode is busy.
            try { sourceCamera.addCallbackBuffer(data) } catch (_: RuntimeException) { }
            return
        }
        try {
            decoder.execute {
                var invite: String? = null
                try {
                    if (!disposed.get()) invite = decodeQr(data, previewWidth, previewHeight)
                } finally {
                    if (invite != null && isValidInvite(invite!!) && delivered.compareAndSet(false, true)) {
                        mainHandler.post {
                            if (!disposed.get()) {
                                StilleLog.i("qr", "scan-complete")
                                stopCamera()
                                onDecoded(invite!!)
                            }
                        }
                    }
                    mainHandler.post {
                        decodeInFlight.set(false)
                        if (!disposed.get() && callbackEnabled.get() && camera === sourceCamera) {
                            try { sourceCamera.addCallbackBuffer(data) } catch (_: RuntimeException) { }
                        }
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            decodeInFlight.set(false)
            if (!disposed.get() && callbackEnabled.get() && camera === sourceCamera) {
                try { sourceCamera.addCallbackBuffer(data) } catch (_: RuntimeException) { }
            }
        }
    }

    private fun stopCamera() {
        callbackEnabled.set(false)
        val old = camera
        camera = null
        decodeInFlight.set(false)
        if (old != null) {
            try { old.setPreviewCallbackWithBuffer(null) } catch (_: RuntimeException) { }
            try { old.stopPreview() } catch (_: RuntimeException) { }
            try { old.release() } catch (_: RuntimeException) { }
        }
    }

    private fun onErrorOnMain() {
        if (Looper.myLooper() == Looper.getMainLooper()) onError()
        else mainHandler.post { if (!disposed.get()) onError() }
    }

    private fun previewBufferSize(): Int = (previewWidth * previewHeight * 3 / 2) + 1

    private fun backCameraId(): Int? {
        val count = Camera.getNumberOfCameras()
        val info = Camera.CameraInfo()
        for (id in 0 until count) {
            Camera.getCameraInfo(id, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return id
        }
        return if (count > 0) 0 else null
    }

    private fun decodeQr(data: ByteArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || data.size < width * height) return null
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE),
            DecodeHintType.CHARACTER_SET to "UTF-8",
        )
        val reader = QRCodeReader()
        return try {
            decodeWith(reader, source, hints).text
        } catch (_: ReaderException) {
            try {
                reader.reset()
                BinaryBitmap(GlobalHistogramBinarizer(source)).let { reader.decode(it, hints).text }
            } catch (_: ReaderException) {
                null
            }
        }
    }

    private fun decodeWith(
        reader: QRCodeReader,
        source: PlanarYUVLuminanceSource,
        hints: Map<DecodeHintType, Any>,
    ): Result = reader.decode(BinaryBitmap(HybridBinarizer(source)), hints)

    private fun isValidInvite(value: String): Boolean = validInviteTarget(value)

    private companion object {
        const val OPEN_ATTEMPTS = 2
        const val CAMERA_RETRY_DELAY_MS = 250L
    }
}

/** Shape-only validation; core remains the authority on invite validity. */
internal fun isStilleInvite(value: String): Boolean {
    val prefix = INVITE_PREFIXES.firstOrNull { value.startsWith(it) } ?: return false
    return value.length > prefix.length &&
        value.length <= 4096 &&
        value.none { it.isWhitespace() || it.isISOControl() }
}

private fun hasCamera(context: Context): Boolean {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) return false
    return try { Camera.getNumberOfCameras() > 0 } catch (_: RuntimeException) { false }
}
