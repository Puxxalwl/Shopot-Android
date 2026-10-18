package com.stille.messenger.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Running(val received: Long, val total: Long?) : DownloadState()
    data class Ready(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
data class UpdateUiState(val result: UpdateResult? = null, val checking: Boolean = false, val download: DownloadState = DownloadState.Idle, val showSheet: Boolean = false, val checkedThisLaunch: Boolean = false)

class UpdateController : ViewModel() {
    private val checker = GitHubReleaseChecker()
    private val mutableState = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()
    private var checkJob: kotlinx.coroutines.Job? = null
    private var downloadJob: kotlinx.coroutines.Job? = null
    private var autoEnabled = true

    fun launchCheck(enabled: Boolean) {
        autoEnabled = enabled
        if (enabled && !mutableState.value.checkedThisLaunch) check(manual = false)
    }
    fun markAutoCheckDeferred() { mutableState.value = mutableState.value.copy(checkedThisLaunch = false) }
    fun setAutoEnabled(enabled: Boolean) {
        autoEnabled = enabled
        if (!enabled) mutableState.value = mutableState.value.copy(showSheet = false)
        else if (!mutableState.value.checkedThisLaunch) check(manual = false)
    }
    fun check(manual: Boolean) {
        if (checkJob?.isActive == true) return
        if (!manual && !autoEnabled) return
        mutableState.value = mutableState.value.copy(checking = true, result = null, showSheet = false)
        checkJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { checker.check() }
            val allowAuto = autoEnabled
            val dismissed = mutableState.value.showSheet
            mutableState.value = mutableState.value.copy(result = result, checking = false,
                checkedThisLaunch = mutableState.value.checkedThisLaunch || !manual,
                showSheet = allowAuto && !manual && !dismissed && result is UpdateResult.Available)
        }
    }
    fun dismissSheet() { mutableState.value = mutableState.value.copy(showSheet = false) }
    fun startDownload(context: Context) {
        val release = (mutableState.value.result as? UpdateResult.Available)?.release ?: return
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(download = DownloadState.Running(0, release.size))
            val result = withContext(Dispatchers.IO) { download(context, release) }
            mutableState.value = mutableState.value.copy(download = result)
        }
    }
    fun cancelDownload() { downloadJob?.cancel(); downloadJob = null; mutableState.value = mutableState.value.copy(download = DownloadState.Idle) }

    private suspend fun download(context: Context, release: ReleaseInfo): DownloadState {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val temp = File(directory, "stille-update.apk.part")
        val target = File(directory, "stille-update.apk")
        var connection: HttpURLConnection? = null
        try {
            connection = URL(release.apkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000; connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            var redirects = 0
            while (connection?.responseCode in 300..399) {
                val current = connection ?: error("Download connection unavailable")
                check(++redirects <= 5) { "Too many download redirects" }
                val location = current.getHeaderField("Location") ?: error("Invalid download redirect")
                val redirected = URL(URL(current.url.toString()), location)
                if (redirected.protocol != "https" || redirected.host !in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")) error("Unsafe download redirect")
                connection.disconnect()
                connection = redirected.openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000; connection.readTimeout = 30_000
                connection.instanceFollowRedirects = false
            }
            val activeConnection = connection ?: error("Download connection unavailable")
            check(activeConnection.responseCode in 200..299) { "Download failed: HTTP ${activeConnection.responseCode}" }
            val length = activeConnection.contentLengthLong.takeIf { it > 0 }
            check(length == null || length <= MAX_APK_BYTES) { "APK exceeds 50 MiB" }
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            activeConnection.inputStream.use { input -> FileOutputStream(temp).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    received += count; check(received <= MAX_APK_BYTES) { "APK exceeds 50 MiB" }
                    output.write(buffer, 0, count); digest.update(buffer, 0, count)
                    mutableState.value = mutableState.value.copy(download = DownloadState.Running(received, length))
                }
            } }
            check(length == null || received == length) { "Incomplete APK download" }
            check(digest.digest().joinToString("") { "%02x".format(it) } == release.digest) { "APK checksum mismatch" }
            check(context.packageManager.canRequestPackageInstalls()) { "Allow installs from Stille in Android settings" }
            check(Build.VERSION.SDK_INT >= 26)
            check(context.packageManager.getPackageArchiveInfo(temp.absolutePath, 0)?.packageName == context.packageName) { "APK is not a Stille update" }
            check(Build.VERSION.SDK_INT >= 28)
            val apkInfo = context.packageManager.getPackageArchiveInfo(temp.absolutePath, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES) ?: error("Invalid APK")
            val installed = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            val apkSigners = apkInfo.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            val currentSigners = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            check(apkSigners.isNotEmpty() && apkSigners == currentSigners) { "APK signing certificate does not match this installation" }
            check(apkInfo.longVersionCode > installed.longVersionCode) { "APK is not newer than installed version" }
            check(apkInfo.applicationInfo?.minSdkVersion?.let { Build.VERSION.SDK_INT >= it } != false) { "Android version is too old" }
            check(temp.renameTo(target)) { "Could not finalize APK" }
            return DownloadState.Ready(target)
        } catch (e: kotlinx.coroutines.CancellationException) { temp.delete(); throw e }
        catch (e: Exception) { temp.delete(); return DownloadState.Error(e.message ?: "Download failed") }
        finally { connection?.disconnect() }
    }
    companion object { const val MAX_APK_BYTES = 50L * 1024 * 1024 }
}

fun installUpdate(context: Context, file: File): Intent {
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
}
