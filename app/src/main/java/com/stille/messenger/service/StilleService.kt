package com.stille.messenger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import android.os.IBinder
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
    // ACCEPT_INVITE creates a local pending contact before the remote JOIN is authenticated.
    private val pendingContactIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    private val onlineContactIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
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

    fun retryCore() {
        if (!_isCoreReady.value && startJob?.isActive != true) startCoreIfNeeded()
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
            } catch (e: TimeoutCancellationException) {
                _inviteState.update { it.copy(busy = false, error = InviteError.REQUEST_FAILED) }
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
                val cid = Responses.decodeAcceptInvite(response.second)
                // A cid alone is not authenticated identity: keep it pending until JOIN/PRESENCE.
                pendingContactIds.add(cid)
                _inviteState.value = _inviteState.value.copy(busy = false, accepted = true)
            } catch (e: TimeoutCancellationException) {
                _inviteState.update { it.copy(busy = false, error = InviteError.REQUEST_FAILED) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _inviteState.value = _inviteState.value.copy(busy = false, error = inviteError(e))
            } finally { inviteMutex.unlock() }
        }
    }

    private fun requestContactSnapshot() {
        serviceScope.launch {
            try {
                val response = sendRequest(Method.LIST_CONTACTS, Requests.listContacts())
                val contacts = Responses.decodeListContacts(response.second)
                _confirmedContacts.value = contacts.filter { it.cid > 0 }.takeLast(200)
            } catch (_: Exception) { /* next core event or retry will refresh */ }
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
    private var multicastLock: WifiManager.MulticastLock? = null
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
            ACTION_START, null -> {
                // START_STICKY may recreate the service with a null intent.
                // Treat that as a normal restart so the local core becomes ready again.
                startForeground(NOTIF_ID, buildNotification())
                startCoreIfNeeded()
            }
        }
        return START_STICKY
    }

    // ── Core binary lifecycle ────────────────────────────────────────────
    @Synchronized
    private fun startCoreIfNeeded() {
        if (_isCoreReady.value || startJob?.isActive == true) return
        startJob = serviceScope.launch {
            var lastError: Exception? = null
            repeat(CORE_START_ATTEMPTS) { attempt ->
                var stage = "package"
                try {
                    // A previous failed launch can leave a stale socket or process
                    // behind. Clean both before each attempt so connectUds never
                    // reports a false "core unavailable" state.
                    cleanupCoreRuntime()
                    coreDir.mkdirs()
                    coreBinary()
                    stage = "identity"
                    ensureIdentity()
                    stage = "process"
                    acquireMulticastLock()
                    startCoreProcess()
                    stage = "socket"
                    waitForSocket()
                    stage = "handshake"
                    connectUds()
                    _isCoreReady.value = true
                    _inviteState.value = InviteState(ready = true)
                    return@launch
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    lastError = e
                    // Fixed stage and exception type only: no paths, identity or core output.
                    android.util.Log.w("StilleService", "Core startup: $stage (${e.javaClass.simpleName})")
                    cleanupCoreRuntime()
                    if (attempt + 1 < CORE_START_ATTEMPTS) delay(CORE_RETRY_DELAY_MS)
                }
            }
            _inviteState.value = InviteState(error = InviteError.CORE_UNAVAILABLE)
            _events.emit(CoreEvent.Error("Core unavailable: ${lastError?.javaClass?.simpleName ?: "startup failed"}"))
        }
    }

    private fun coreBinary(): File = File(applicationInfo.nativeLibraryDir, "libshopot.so").also {
        if (!it.isFile || !it.canExecute()) throw IOException("Packaged core executable unavailable")
    }

    private fun coreCommand(command: String): ProcessBuilder =
        ProcessBuilder(coreBinary().absolutePath, command)
            .directory(coreDir)
            .redirectErrorStream(true)
            // Core diagnostics can contain identities. Discard without buffers or disk files.
            .redirectOutput(ProcessBuilder.Redirect.to(File("/dev/null")))
            .apply {
                environment()["HOME"] = homeDir.absolutePath
                environment()["PATH"] = "/system/bin:/bin"
            }

    /** Invoke only the core's init command; never replace an existing identity. */
    private suspend fun ensureIdentity() {
        val seed = File(homeDir, ".shopot/zig-seed")
        if (!needsCoreIdentity(seed)) return
        val process = coreCommand("init").start()
        try {
            val finished = withTimeoutOrNull(15_000) {
                while (process.isAlive) delay(50)
                true
            } ?: false
            if (!finished) throw IOException("Core identity initialization timed out")
            if (process.exitValue() != 0 || needsCoreIdentity(seed)) {
                throw IOException("Core identity initialization failed")
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager ?: return
        multicastLock = wifi.createMulticastLock("stille-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun startCoreProcess() {
        coreProcess = coreCommand("serve").start()
    }

    private suspend fun waitForSocket(timeoutMs: Long = 10_000) {
        val socketFile = File(socketPath)
        val start = System.currentTimeMillis()
        while (!socketFile.exists() && System.currentTimeMillis() - start < timeoutMs) {
            if (coreProcess?.isAlive != true) throw IOException("Core exited before socket startup")
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
                    if (ev.online) onlineContactIds.add(ev.cid) else onlineContactIds.remove(ev.cid)
                    // Presence is emitted only by an authenticated session. Promote a locally
                    // accepted contact only after the core has completed the JOIN handshake.
                    if (ev.online && pendingContactIds.remove(ev.cid)) {
                        _inviteState.update { it.copy(accepted = false, joined = true) }
                        val current = _confirmedContacts.value.firstOrNull { it.cid == ev.cid }
                        if (current == null) {
                            requestContactSnapshot()
                        }
                    }
                    _confirmedContacts.update { list ->
                        list.map { if (it.cid == ev.cid) it.copy(online = ev.online, lastSeenMs = ev.lastSeen) else it }
                    }
                    _events.emit(com.stille.messenger.service.CoreEvent.Presence(ev.cid, ev.online, ev.lastSeen))
                }
                4 -> {  // REACTION
                    val ev = com.stille.coreapi.Events.decodeReaction(payload)
                    _events.emit(com.stille.messenger.service.CoreEvent.Reaction(ev.cid, ev.msgId, ev.op, ev.codepoint, ev.tsMs))
                }
                8 -> {  // CONTACT_JOINED
                    val ev = com.stille.coreapi.Events.decodeContactJoined(payload)
                    pendingContactIds.remove(ev.cid)
                    onlineContactIds.add(ev.cid)
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
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
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
        pendingContactIds.clear()
        onlineContactIds.clear()
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
