package com.stille.messenger.backup

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the WebDAV password at rest with an AES-256-GCM key held by the
 * Android Keystore (AGENTS.md: no plaintext secrets on disk). The ciphertext
 * (iv || bytes, base64) lives in the app's private preferences.
 *
 * Storage glue only: this never touches protocol or backup cryptography,
 * which the core owns.
 */
interface CredentialVault {
    fun encrypt(plainText: String): String
    fun decrypt(encoded: String): String

    class DecryptionException(message: String) : Exception(message)
}

class KeystoreCredentialVault : CredentialVault {
    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + cipherText, Base64.NO_WRAP)
    }

    override fun decrypt(encoded: String): String {
        try {
            val all = Base64.decode(encoded, Base64.NO_WRAP)
            if (all.size <= IV_LENGTH_BYTES) throw CredentialVault.DecryptionException("corrupted credential")
            val iv = all.copyOfRange(0, IV_LENGTH_BYTES)
            val cipherText = all.copyOfRange(IV_LENGTH_BYTES, all.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            return String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: CredentialVault.DecryptionException) {
            throw e
        } catch (e: Exception) {
            throw CredentialVault.DecryptionException("corrupted credential")
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "stille-webdav-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}

/** Persists the WebDAV login (URL and user readable, password encrypted). */
object WebDavSettings {
    fun save(context: Context, credentials: WebDavCredentials, vault: CredentialVault) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, credentials.baseUrl)
            .putString(KEY_USER, credentials.username)
            .putString(KEY_PASSWORD, vault.encrypt(credentials.password))
            .apply()
    }

    fun load(context: Context, vault: CredentialVault): WebDavCredentials? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_URL, null) ?: return null
        val user = prefs.getString(KEY_USER, null) ?: return null
        val encoded = prefs.getString(KEY_PASSWORD, null) ?: return null
        val password = try {
            vault.decrypt(encoded)
        } catch (_: Exception) {
            clear(context)
            return null
        }
        return try {
            WebDavCredentials(url, user, password).validated()
        } catch (_: WebDavCredentials.ValidationException) {
            clear(context)
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_URL).remove(KEY_USER).remove(KEY_PASSWORD).apply()
    }

    private const val PREFS = "stille_backup"
    private const val KEY_URL = "webdav_url"
    private const val KEY_USER = "webdav_user"
    private const val KEY_PASSWORD = "webdav_password_enc"
}
