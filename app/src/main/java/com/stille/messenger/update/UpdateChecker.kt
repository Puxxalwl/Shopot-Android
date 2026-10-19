package com.stille.messenger.update

import com.stille.messenger.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

private const val LATEST_RELEASE_URL = "https://api.github.com/repos/Puxxalwl/Stille-Messenger-Android/releases/latest"
private const val APK_PREFIX = "Stille-Messenger-"

internal fun releaseApkAssetName(tag: String): String? =
    tag.takeIf { it.startsWith('v') && Versioning.isValid(it) }?.let { "$APK_PREFIX$it.apk" }
private const val MAX_METADATA = 1024 * 1024

data class ReleaseInfo(val version: String, val apkUrl: String, val digest: String?, val size: Long?)
sealed class UpdateResult {
    data class Available(val release: ReleaseInfo) : UpdateResult()
    data object Current : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

object Versioning {
    fun isValid(value: String): Boolean = value.removePrefix("v").split('.').all { it.isNotEmpty() && it.toIntOrNull() != null }

    fun compare(a: String, b: String): Int {
        fun parts(value: String): List<Int>? {
            val result = value.removePrefix("v").split('.')
            if (result.any { it.isEmpty() || it.toIntOrNull() == null }) return null
            return result.map { it.toInt() }
        }
        val left = parts(a) ?: return 0
        val right = parts(b) ?: return 0
        return (0 until maxOf(left.size, right.size)).firstNotNullOfOrNull { i ->
            (left.getOrElse(i) { 0 }).compareTo(right.getOrElse(i) { 0 }).takeIf { it != 0 }
        } ?: 0
    }
}

class GitHubReleaseChecker {
    fun check(): UpdateResult {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Stille-Android/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode !in 200..299) return UpdateResult.Error("GitHub: HTTP ${connection.responseCode}")
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n); if (output.size() > MAX_METADATA) throw IOException("Response too large") }
                output.toByteArray()
            }
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            if (root.optBoolean("draft") || root.optBoolean("prerelease")) return UpdateResult.Current
            val tag = root.optString("tag_name")
            if (!Versioning.isValid(tag)) return UpdateResult.Error("Invalid release version")
            val version = tag.removePrefix("v")
            val expectedAssetName = releaseApkAssetName(tag)
                ?: return UpdateResult.Error("Invalid release version")
            if (Versioning.compare(version, BuildConfig.VERSION_NAME) <= 0) return UpdateResult.Current
            val assets = root.optJSONArray("assets") ?: return UpdateResult.Error("APK asset is missing")
            var apk: JSONObject? = null
            for (i in 0 until assets.length()) {
                val item = assets.optJSONObject(i) ?: continue
                if (item.optString("name") == expectedAssetName) { apk = item; break }
            }
            val asset = apk ?: return UpdateResult.Error("APK asset is missing")
            val url = asset.optString("browser_download_url")
            if (!url.startsWith("https://github.com/Puxxalwl/Stille-Messenger-Android/releases/download/")) return UpdateResult.Error("Unsafe download URL")
            val digest = asset.optString("digest").removePrefix("sha256:").lowercase().takeIf { it.matches(Regex("[0-9a-f]{64}")) }
                ?: return UpdateResult.Error("APK checksum is missing")
            UpdateResult.Available(ReleaseInfo(version, url, digest, asset.optLong("size").takeIf { it > 0 }))
        } catch (e: Exception) { UpdateResult.Error(e.message ?: "Update check failed") } finally { connection.disconnect() }
    }
}
