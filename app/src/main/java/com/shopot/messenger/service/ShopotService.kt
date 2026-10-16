package com.shopot.messenger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import android.os.IBinder
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import com.shopot.coreapi.FrameHeader
import com.shopot.coreapi.FrameKind
import com.shopot.coreapi.Method
import com.shopot.coreapi.ProtocolException
import com.shopot.coreapi.Requests
import com.shopot.coreapi.Responses
import com.shopot.coreapi.encodeFrame
import com.shopot.coreapi.decodeFrame
import com.shopot.coreapi.CoreVersion
import com.shopot.coreapi.ProtocolStatusException
import com.shopot.messenger.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

class ShopotService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // UDS connection
    private var socket: LocalSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val requestMutex = Mutex()
    private val pendingResponses = ConcurrentHashMap<Int, CompletableDeferred<Pair<FrameHeader, ByteArray>>>()
    private var requestId = 0

    // State exposed to UI
    private val _isCoreReady = MutableStateFlow(false)
    val isCoreReady = _isCoreReady.asStateFlow()

    private val _events = MutableSharedFlow<CoreEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    // ── Core process management ──────────────────────────────────────────
    private var coreProcess: Process? = null
    private lateinit var coreDir: File
    private lateinit var homeDir: File
    private lateinit var socketPath: String

    companion object {
        const val ACTION_START = "com.shopot.messenger.START"
        const val ACTION_STOP = "com.shopot.messenger.STOP"
        const val NOTIF_CHANNEL_ID = "shopot_foreground"
        private const val NOTIF_ID = 1
        private val EMPTY_PAYLOAD = ByteArray(0)

        fun start(context: Context) {
            val intent = Intent(context, ShopotService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ShopotService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        coreDir = File(filesDir, "shopot")
        homeDir = coreDir
        socketPath = "$homeDir/.shopot/api.sock"
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCore()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification())
                startCoreIfNeeded()
            }
        }
        return START_STICKY
    }

    // ── Core binary lifecycle ────────────────────────────────────────────
    private fun startCoreIfNeeded() {
        serviceScope.launch {
            try {
                copyCoreBinaryIfNeeded()
                startCoreProcess()
                waitForSocket()
                connectUds()
                _isCoreReady.value = true
            } catch (e: Exception) {
                _events.emit(CoreEvent.Error(e.message ?: "Unknown error"))
            }
        }
    }

    private fun copyCoreBinaryIfNeeded() {
        val binaryFile = File(coreDir, "shopot")
        if (!binaryFile.exists()) {
            assets.open("shopot").use { input ->
                binaryFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        // Ensure executable permissions
        val mode = 0x1FF  // 0755
        try {
            Os.chmod(binaryFile.absolutePath, mode)
        } catch (e: ErrnoException) {
            // Fallback to reflection if chmod fails
            binaryFile.setExecutable(true, false)
        }
    }

    private fun startCoreProcess() {
        val binaryFile = File(coreDir, "shopot")
        if (!binaryFile.exists()) {
            throw RuntimeException("shopot binary not found")
        }

        val processBuilder = ProcessBuilder(binaryFile.absolutePath, "serve")
        processBuilder.environment()["HOME"] = homeDir.absolutePath
        processBuilder.environment()["PATH"] = "/system/bin:/bin"
        processBuilder.redirectErrorStream(true)

        coreProcess = processBuilder.start()

        // Read any core output for diagnostics (redacted)
        serviceScope.launch {
            try {
                coreProcess?.inputStream?.bufferedReader()
                    ?.useLines { lines ->
                        lines.forEach { line ->
                            // Diagnostics only — no sensitive data from core
                            android.util.Log.d("ShopotService", "core: ${line.take(120)}")
                        }
                    }
            } catch (_: Exception) { }
        }
    }

    private suspend fun waitForSocket(timeoutMs: Long = 10_000) {
        val socketFile = File(socketPath)
        val start = System.currentTimeMillis()
        while (!socketFile.exists() && System.currentTimeMillis() - start < timeoutMs) {
            delay(100)
        }
        if (!socketFile.exists()) {
            throw RuntimeException("Core socket not ready after ${timeoutMs}ms")
        }
    }

    // ── UDS connection ───────────────────────────────────────────────────
    private suspend fun connectUds() {
        socket = LocalSocket()
        try {
            socket?.connect(LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM))
            inputStream = socket?.inputStream
            outputStream = socket?.outputStream

            // One reader owns the UDS input stream; responses are correlated by request id.
            startEventListener()

            // Ready probe: GET_VERSION
            val versionResp = sendRequest(Method.GET_VERSION, Requests.getVersion())
            val version = Responses.decodeGetVersion(versionResp.second)
            if (version.major != 2) {
                throw ProtocolException("incompatible core version: ${version.major}.${version.minor}.${version.patch}")
            }
            android.util.Log.i("ShopotService", "Core version: ${version.major}.${version.minor}.${version.patch}")

            // Bootstrap: LIST_CONTACTS
            val contactsResp = sendRequest(Method.LIST_CONTACTS, Requests.listContacts())
            val contacts = Responses.decodeListContacts(contactsResp.second)
            _events.tryEmit(CoreEvent.Ready(version, contacts))

        } catch (e: Exception) {
            socket?.close()
            throw e
        }
    }

    private fun startEventListener() {
        val input = inputStream ?: return
        serviceScope.launch {
            while (isActive) {
                try {
                    val frame = readFrame(input) ?: break
                    val (header, payload) = decodeFrame(frame)
                    when (header.kind) {
                        FrameKind.EVENT -> dispatchEvent(header.id, payload)
                        FrameKind.RESPONSE -> pendingResponses[header.requestId]?.complete(header to payload)
                        else -> throw ProtocolException("unexpected frame kind from core: ${header.kind}")
                    }
                } catch (e: Exception) {
                    _events.emit(CoreEvent.Error("UDS read error: ${e.message}"))
                    break
                }
            }
        }
    }

    private fun dispatchEvent(eventId: Int, payload: ByteArray) {
        serviceScope.launch {
            when (eventId) {
                1 -> {  // MESSAGE_RECEIVED
                    val ev = com.shopot.coreapi.Events.decodeMessageReceived(payload)
                    _events.emit(com.shopot.messenger.service.CoreEvent.MessageReceived(ev.message, ev.senderName))
                }
                2 -> {  // MESSAGE_DELIVERED
                    val ev = com.shopot.coreapi.Events.decodeMessageDelivered(payload)
                    _events.emit(com.shopot.messenger.service.CoreEvent.MessageDelivered(ev.cid, ev.msgId))
                }
                3 -> {  // PRESENCE
                    val ev = com.shopot.coreapi.Events.decodePresence(payload)
                    _events.emit(com.shopot.messenger.service.CoreEvent.Presence(ev.cid, ev.online, ev.lastSeen))
                }
                4 -> {  // REACTION
                    val ev = com.shopot.coreapi.Events.decodeReaction(payload)
                    _events.emit(com.shopot.messenger.service.CoreEvent.Reaction(ev.cid, ev.msgId, ev.op, ev.codepoint, ev.tsMs))
                }
                8 -> {  // CONTACT_JOINED
                    val ev = com.shopot.coreapi.Events.decodeContactJoined(payload)
                    _events.emit(com.shopot.messenger.service.CoreEvent.ContactJoined(ev.cid, ev.name))
                }
                12 -> { // CONTACT_CARD
                    val card = com.shopot.coreapi.Events.decodeContactCard(payload)
                    _events.emit(com.shopot.messenger.service.CoreEvent.ContactCard(card))
                }
                6 -> {  // AVATAR_RECEIVED
                    val avatar = com.shopot.coreapi.Events.decodeAvatarReceived(payload)
                    _events.emit(com.shopot.messenger.service.CoreEvent.AvatarReceived(avatar))
                }
                7 -> {  // FILE_RECEIVED
                    val fi = com.shopot.coreapi.Events.decodeFileReceived(payload)
                    _events.emit(com.shopot.messenger.service.CoreEvent.FileReceived(fi))
                }
                11 -> { // FILE_SENT
                    val fi = com.shopot.coreapi.Events.decodeFileSent(payload)
                    _events.emit(com.shopot.messenger.service.CoreEvent.FileSent(fi))
                }
                // Add CALL_STATE (5), CONTACT_PROFILE (9), BLOCKED_BY_PEER (10) as needed
                else -> {
                    android.util.Log.d("ShopotService", "Unhandled event id=$eventId, payloadLen=${payload.size}")
                }
            }
        }
    }

    // ── UDS read/write helpers ───────────────────────────────────────────
    private suspend fun readFrame(input: InputStream): ByteArray? = withContext(Dispatchers.IO) {
        val frame = ByteArray(16)
        readFully(input, frame, 0, frame.size) ?: return@withContext null
        val payloadLen =
            (frame[12].toInt() and 0xFF) or
                ((frame[13].toInt() and 0xFF) shl 8) or
                ((frame[14].toInt() and 0xFF) shl 16) or
                ((frame[15].toInt() and 0xFF) shl 24)
        if (payloadLen !in 0..com.shopot.coreapi.MAX_PAYLOAD) {
            throw ProtocolException("payload too large: $payloadLen")
        }
        if (payloadLen == 0) return@withContext frame
        val fullFrame = ByteArray(16 + payloadLen)
        frame.copyInto(fullFrame, 0)
        readFully(input, fullFrame, 16, payloadLen) ?: return@withContext null
        fullFrame
    }

    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int, length: Int): Boolean? {
        var read = 0
        while (read < length) {
            val n = input.read(buffer, offset + read, length - read)
            if (n < 0) return null
            if (n == 0) continue
            read += n
        }
        return true
    }

    /**
     * Send a request to the core daemon and receive the response.
     * Throws [ProtocolException] with a human-readable status name if the
     * response status is non-zero.
     */
    suspend fun sendRequest(method: Method, payload: ByteArray = EMPTY_PAYLOAD): Pair<FrameHeader, ByteArray> {
        val deferred: CompletableDeferred<Pair<FrameHeader, ByteArray>>
        val reqId: Int
        requestMutex.withLock {
            reqId = nextRequestId()
            deferred = CompletableDeferred()
            pendingResponses[reqId] = deferred
            try {
                val frame = encodeFrame(FrameKind.REQUEST, method.id, reqId, 0, payload)
                val output = outputStream ?: throw ProtocolException("core socket not connected")
                output.write(frame)
                output.flush()
            } catch (error: Throwable) {
                pendingResponses.remove(reqId)
                throw error
            }
        }

        return try {
            val (header, responsePayload) = withTimeout(30_000) { deferred.await() }
            if (header.requestId != reqId) throw ProtocolException("request correlation mismatch")
            if (header.status != com.shopot.coreapi.status.OK) {
                throw ProtocolStatusException(
                    header.status,
                    "core error: ${com.shopot.coreapi.status.name(header.status)}"
                )
            }
            header to responsePayload
        } finally {
            pendingResponses.remove(reqId)
        }
    }

    private fun nextRequestId(): Int {
        requestId = if (requestId == Int.MAX_VALUE) 1 else requestId + 1
        return requestId
    }

    private fun stopCore() {
        try {
            socket?.close()
        } catch (_: Exception) { }
        inputStream = null
        outputStream = null
        pendingResponses.values.forEach { it.cancel() }
        pendingResponses.clear()
        _isCoreReady.value = false
        coreProcess?.destroy()
        coreProcess = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopCore()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_channel_name))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setOngoing(true)
            .build()
    }
}

// ── CoreEvent wrapper for UI consumption ──────────────────────────────────
sealed interface CoreEvent {
    data class Ready(val version: CoreVersion, val contacts: List<com.shopot.coreapi.Contact>) : CoreEvent
    data class MessageReceived(val message: com.shopot.coreapi.Message, val senderName: String) : CoreEvent
    data class MessageDelivered(val cid: Int, val msgId: ByteArray) : CoreEvent
    data class Presence(val cid: Int, val online: Boolean, val lastSeen: Long) : CoreEvent
    data class Reaction(val cid: Int, val msgId: ByteArray, val op: Int, val codepoint: Int, val tsMs: Long) : CoreEvent
    data class ContactJoined(val cid: Int, val name: String) : CoreEvent
    data class ContactCard(val card: com.shopot.coreapi.ContactCard) : CoreEvent
    data class AvatarReceived(val avatar: com.shopot.coreapi.AvatarData) : CoreEvent
    data class FileReceived(val info: com.shopot.coreapi.FileInfo) : CoreEvent
    data class FileSent(val info: com.shopot.coreapi.FileInfo) : CoreEvent
    data class Error(val message: String) : CoreEvent
}
