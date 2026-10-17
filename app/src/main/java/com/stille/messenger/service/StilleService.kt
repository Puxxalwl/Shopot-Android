package com.stille.messenger.service

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
import com.stille.coreapi.FrameHeader
import com.stille.coreapi.FrameKind
import com.stille.coreapi.Method
import com.stille.coreapi.ProtocolException
import com.stille.coreapi.Requests
import com.stille.coreapi.Responses
import com.stille.coreapi.encodeFrame
import com.stille.coreapi.decodeFrame
import com.stille.coreapi.CoreVersion
import com.stille.coreapi.ProtocolStatusException
import com.stille.messenger.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

class StilleService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // UDS connection
    private var socket: LocalSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val requestMutex = Mutex()
    private val pendingResponses = ConcurrentHashMap<Int, CompletableDeferred<Pair<FrameHeader, ByteArray>>>()
    private var requestId = 0

    // State exposed to UI
    private val _inviteState = MutableStateFlow(InviteState())
    val inviteState = _inviteState.asStateFlow()
    private val inviteMutex = Mutex()
    private var startJob: Job? = null
    private val _confirmedContacts = MutableStateFlow<List<com.stille.coreapi.Contact>>(emptyList())
    val confirmedContacts = _confirmedContacts.asStateFlow()
    private val _avatars = MutableStateFlow<Map<Int, ByteArray>>(emptyMap())
    val avatars = _avatars.asStateFlow()

    private val _avatarState = MutableStateFlow(AvatarState())
    val avatarState = _avatarState.asStateFlow()
    private val avatarMutex = Mutex()

    fun setAvatar(uri: android.net.Uri) {
        serviceScope.launch {
            if (!avatarMutex.tryLock()) return@launch
            try {
                if (!_isCoreReady.value) {
                    _avatarState.value = AvatarState(error = AvatarError.CORE_UNAVAILABLE)
                    return@launch
                }
                _avatarState.value = AvatarState(busy = true)
                val data = com.stille.messenger.ui.prepareAvatar(contentResolver, uri)
                if (data == null) {
                    _avatarState.value = AvatarState(error = AvatarError.INVALID_IMAGE)
                    return@launch
                }
                val response = sendRequest(Method.SET_AVATAR, Requests.setAvatar(data))
                val (avatarId, size) = Responses.decodeSetAvatar(response.second)
                if (size != data.size || !matchesAvatarId(avatarId, data)) throw ProtocolException("Invalid avatar acknowledgement")
                if (!_isCoreReady.value) throw ProtocolException("Core disconnected")
                _avatars.update { cacheAvatar(it, 0, data) }
                _avatarState.value = AvatarState(saved = true)
            } catch (e: TimeoutCancellationException) {
                _avatarState.value = AvatarState(error = AvatarError.REQUEST_FAILED)
            } catch (e: CancellationException) {
                _avatarState.value = AvatarState()
                throw e
            } catch (_: Exception) {
                _avatarState.value = AvatarState(error = AvatarError.REQUEST_FAILED)
            } finally { avatarMutex.unlock() }
        }
    }

    fun createInvite(name: String) {
        serviceScope.launch {
            if (!inviteMutex.tryLock()) return@launch
            try {
                if (!_isCoreReady.value) {
                    _inviteState.value = _inviteState.value.copy(error = InviteError.CORE_UNAVAILABLE)
                    return@launch
                }
                // Reuse the live code instead of exhausting the four-invite core registry.
                if (_inviteState.value.code != null) return@launch
                _inviteState.value = _inviteState.value.copy(busy = true, error = null, joined = false)
                val response = sendRequest(Method.CREATE_INVITE, Requests.createInvite(name, ""))
                val code = Responses.decodeCreateInvite(response.second)
                _inviteState.value = _inviteState.value.copy(code = code, busy = false)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _inviteState.value = _inviteState.value.copy(busy = false, error = inviteError(e))
            } finally { inviteMutex.unlock() }
        }
    }

    fun acceptInvite(code: String, name: String) {
        serviceScope.launch {
            if (!inviteMutex.tryLock()) return@launch
            try {
                if (!_isCoreReady.value) {
                    _inviteState.value = _inviteState.value.copy(error = InviteError.CORE_UNAVAILABLE)
                    return@launch
                }
                if (!validInviteInput(code.trim())) {
                    _inviteState.value = _inviteState.value.copy(error = InviteError.INVALID_CODE)
                    return@launch
                }
                _inviteState.value = _inviteState.value.copy(busy = true, error = null, accepted = false, joined = false)
                val response = sendRequest(Method.ACCEPT_INVITE, Requests.acceptInvite(code.trim(), name))
                Responses.decodeAcceptInvite(response.second)
                // A cid alone is not authenticated identity: wait for CONTACT_JOINED.
                _inviteState.value = _inviteState.value.copy(busy = false, accepted = true)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _inviteState.value = _inviteState.value.copy(busy = false, error = inviteError(e))
            } finally { inviteMutex.unlock() }
        }
    }

    private fun inviteError(error: Exception): InviteError = when ((error as? ProtocolStatusException)?.code) {
        com.stille.coreapi.status.BAD_INVITE_CODE -> InviteError.INVALID_CODE
        com.stille.coreapi.status.TOO_MANY_INVITES -> InviteError.LIMIT_REACHED
        else -> InviteError.REQUEST_FAILED
    }

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
        const val ACTION_START = "com.stille.messenger.START"
        const val ACTION_STOP = "com.stille.messenger.STOP"
        const val NOTIF_CHANNEL_ID = "stille_foreground"
        private const val NOTIF_ID = 1
        private const val CORE_START_ATTEMPTS = 3
        private const val CORE_RETRY_DELAY_MS = 250L
        private val EMPTY_PAYLOAD = ByteArray(0)

        fun start(context: Context) {
            val intent = Intent(context, StilleService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StilleService::class.java).apply {
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
                startJob?.cancel()
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
        if (_isCoreReady.value || startJob?.isActive == true) return
        startJob = serviceScope.launch {
            var lastError: Exception? = null
            repeat(CORE_START_ATTEMPTS) { attempt ->
                try {
                    // A previous failed launch can leave a stale socket or process
                    // behind. Clean both before each attempt so connectUds never
                    // reports a false "core unavailable" state.
                    cleanupCoreRuntime()
                    coreDir.mkdirs()
                    copyCoreBinaryIfNeeded()
                    startCoreProcess()
                    waitForSocket()
                    connectUds()
                    _isCoreReady.value = true
                    _inviteState.value = InviteState(ready = true)
                    return@launch
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    lastError = e
                    cleanupCoreRuntime()
                    if (attempt + 1 < CORE_START_ATTEMPTS) delay(CORE_RETRY_DELAY_MS)
                }
            }
            _inviteState.value = InviteState(error = InviteError.CORE_UNAVAILABLE)
            _events.emit(CoreEvent.Error("Core unavailable: ${lastError?.javaClass?.simpleName ?: "startup failed"}"))
        }
    }

    private fun copyCoreBinaryIfNeeded() {
        val binaryFile = File(coreDir, "shopot")
        if (!binaryFile.exists() || binaryFile.length() == 0L) {
            // Prefer the asset (the build script places the core there), but
            // accept the ABI-specific packaged native binary as a fallback.
            // This keeps already-installed APKs working when only jniLibs was
            // packaged by the Android build configuration.
            val nativeBinary = File(applicationInfo.nativeLibraryDir, "shopot")
            if (nativeBinary.isFile && nativeBinary.length() > 0L) {
                nativeBinary.inputStream().use { input ->
                    binaryFile.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                assets.open("shopot").use { input ->
                    binaryFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        if (!binaryFile.isFile || binaryFile.length() == 0L) {
            throw IOException("shopot core binary is missing from the APK")
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
                            // Drain stdout without exposing possible invite secrets or identities.
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
            android.util.Log.i("StilleService", "Core version: ${version.major}.${version.minor}.${version.patch}")

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

    // Preserve wire ordering: a following avatar must not overtake JOIN/profile.
    private suspend fun dispatchEvent(eventId: Int, payload: ByteArray) {
        when (eventId) {
                1 -> {  // MESSAGE_RECEIVED
                    val ev = com.stille.coreapi.Events.decodeMessageReceived(payload)
                    _events.emit(com.stille.messenger.service.CoreEvent.MessageReceived(ev.message, ev.senderName))
                }
                2 -> {  // MESSAGE_DELIVERED
                    val ev = com.stille.coreapi.Events.decodeMessageDelivered(payload)
                    _events.emit(com.stille.messenger.service.CoreEvent.MessageDelivered(ev.cid, ev.msgId))
                }
                3 -> {  // PRESENCE
                    val ev = com.stille.coreapi.Events.decodePresence(payload)
                    _events.emit(com.stille.messenger.service.CoreEvent.Presence(ev.cid, ev.online, ev.lastSeen))
                }
                4 -> {  // REACTION
                    val ev = com.stille.coreapi.Events.decodeReaction(payload)
                    _events.emit(com.stille.messenger.service.CoreEvent.Reaction(ev.cid, ev.msgId, ev.op, ev.codepoint, ev.tsMs))
                }
                8 -> {  // CONTACT_JOINED
                    val ev = com.stille.coreapi.Events.decodeContactJoined(payload)
                    _confirmedContacts.value = (_confirmedContacts.value.filterNot { it.cid == ev.cid } +
                        com.stille.coreapi.Contact(ev.cid, true, false, 0L, ev.name)).takeLast(200)
                    _inviteState.value = _inviteState.value.copy(code = null, accepted = false, joined = true)
                    _events.emit(com.stille.messenger.service.CoreEvent.ContactJoined(ev.cid, ev.name))
                }
                12 -> { // CONTACT_CARD
                    val card = com.stille.coreapi.Events.decodeContactCard(payload)
                    _events.emit(com.stille.messenger.service.CoreEvent.ContactCard(card))
                }
                6 -> {  // AVATAR_RECEIVED
                    val avatar = com.stille.coreapi.Events.decodeAvatarReceived(payload)
                    // Never expose a peer image before authenticated JOIN, or trust an arbitrary image id.
                    if (_isCoreReady.value && _confirmedContacts.value.any { it.cid == avatar.cid } &&
                        matchesAvatarId(avatar.avatarId, avatar.data) &&
                        com.stille.messenger.ui.validAvatarDimensions(avatar.data)) {
                        _avatars.update { cacheAvatar(it, avatar.cid, avatar.data) }
                        _events.emit(com.stille.messenger.service.CoreEvent.AvatarReceived(avatar))
                    }
                }
                7 -> {  // FILE_RECEIVED
                    val fi = com.stille.coreapi.Events.decodeFileReceived(payload)
                    _events.emit(com.stille.messenger.service.CoreEvent.FileReceived(fi))
                }
                11 -> { // FILE_SENT
                    val fi = com.stille.coreapi.Events.decodeFileSent(payload)
                    _events.emit(com.stille.messenger.service.CoreEvent.FileSent(fi))
                }
                9 -> { // CONTACT_PROFILE: invalidate stale image, never display an unconfirmed identity.
                    val profile = com.stille.coreapi.Events.decodeContactProfile(payload)
                    if (_confirmedContacts.value.any { it.cid == profile.cid }) {
                        _avatars.update { cached ->
                            val image = cached[profile.cid]
                            if (image != null && !matchesAvatarId(profile.avatarId, image)) cached - profile.cid else cached
                        }
                    }
                }
                // Add CALL_STATE (5), BLOCKED_BY_PEER (10) as needed
                else -> {
                    android.util.Log.d("StilleService", "Unhandled event id=$eventId, payloadLen=${payload.size}")
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
        if (payloadLen !in 0..com.stille.coreapi.MAX_PAYLOAD) {
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
    suspend fun sendRequest(method: Method, payload: ByteArray = EMPTY_PAYLOAD): Pair<FrameHeader, ByteArray> = withContext(Dispatchers.IO) {
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

        try {
            val (header, responsePayload) = withTimeout(30_000) { deferred.await() }
            if (header.requestId != reqId) throw ProtocolException("request correlation mismatch")
            if (header.status != com.stille.coreapi.status.OK) {
                throw ProtocolStatusException(
                    header.status,
                    "core error: ${com.stille.coreapi.status.name(header.status)}"
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

    private fun cleanupCoreRuntime() {
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        inputStream = null
        outputStream = null
        pendingResponses.values.forEach { it.cancel() }
        pendingResponses.clear()
        coreProcess?.let { process ->
            if (process.isAlive) process.destroy()
            if (process.isAlive) process.destroyForcibly()
        }
        coreProcess = null
        // The core recreates this path on startup. Remove only this exact,
        // app-owned socket path; never recursively delete the data directory.
        File(socketPath).delete()
    }

    private fun stopCore() {
        cleanupCoreRuntime()
        startJob?.cancel()
        startJob = null
        _isCoreReady.value = false
        _inviteState.value = InviteState(error = InviteError.CORE_UNAVAILABLE)
        _confirmedContacts.value = emptyList()
        _avatars.value = emptyMap()
        _avatarState.value = AvatarState()
    }

    inner class LocalBinder : android.os.Binder() {
        val service: StilleService get() = this@StilleService
    }
    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopCore()
        serviceScope.cancel()
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
    data class Ready(val version: CoreVersion, val contacts: List<com.stille.coreapi.Contact>) : CoreEvent
    data class MessageReceived(val message: com.stille.coreapi.Message, val senderName: String) : CoreEvent
    data class MessageDelivered(val cid: Int, val msgId: ByteArray) : CoreEvent
    data class Presence(val cid: Int, val online: Boolean, val lastSeen: Long) : CoreEvent
    data class Reaction(val cid: Int, val msgId: ByteArray, val op: Int, val codepoint: Int, val tsMs: Long) : CoreEvent
    data class ContactJoined(val cid: Int, val name: String) : CoreEvent
    data class ContactCard(val card: com.stille.coreapi.ContactCard) : CoreEvent
    data class AvatarReceived(val avatar: com.stille.coreapi.AvatarData) : CoreEvent
    data class FileReceived(val info: com.stille.coreapi.FileInfo) : CoreEvent
    data class FileSent(val info: com.stille.coreapi.FileInfo) : CoreEvent
    data class Error(val message: String) : CoreEvent
}
