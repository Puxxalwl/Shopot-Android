package com.stille.messenger.backup

import android.app.Activity
import android.util.Base64
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import java.security.SecureRandom
import org.json.JSONObject

/**
 * Passkey-based unlock secret for the core-sealed recovery blob
 * (WebAuthn `prf` extension, CLIENT_RULES.md §4.1–4.2).
 *
 * The passkey lives in the user's Google account (synced by Google
 * Password Manager) and is created once, at enrollment, together with a
 * random 32-byte PRF salt saved in the public sidecar. At backup creation
 * and at restore the same salt is evaluated through the PRF; the 32-byte
 * result IS the unlock secret — the core derives the AEAD key from it.
 * The secret never touches disk and Google only ever sees the sealed blob.
 *
 * When PRF is unavailable the flow falls back to a random recovery code
 * (see [RecoveryCode]) shown to the user once.
 */
class PasskeyBackupKeys(private val rpId: String) {

    class PasskeyException(
        message: String,
        cause: Throwable? = null,
        val cancelled: Boolean = false
    ) : Exception(message, cause)

    /** Create the recovery passkey; true when the PRF extension is supported by it. */
    suspend fun createPasskey(activity: Activity): Boolean {
        val requestJson = JSONObject().apply {
            put("challenge", b64url(random32()))
            put("rp", JSONObject().put("name", "Stille").put("id", rpId))
            put("user", JSONObject()
                .put("id", b64url(random32()))
                .put("name", "stille-recovery")
                .put("displayName", "Stille account recovery"))
            put("pubKeyCredParams", org.json.JSONArray()
                .put(JSONObject().put("type", "public-key").put("alg", -7))
                .put(JSONObject().put("type", "public-key").put("alg", -257)))
            put("attestation", "none")
            put("extensions", JSONObject().put("prf", JSONObject()))
        }
        val manager = CredentialManager.create(activity)
        val response = try {
            manager.createCredential(
                activity,
                CreatePublicKeyCredentialRequest(requestJson.toString())
            )
        } catch (e: CreateCredentialException) {
            throw PasskeyException("passkey creation failed", e,
                cancelled = e is androidx.credentials.exceptions.CreateCredentialCancellationException)
        }
        val credential = response as? CreatePublicKeyCredentialResponse
            ?: throw PasskeyException("unexpected credential type")
        // clientExtensionResults.prf.enabled == true means PRF will work on get().
        return prfEnabledInRegistration(credential.registrationResponseJson)
    }

    /**
     * Run the passkey ceremony with the sidecar salt and obtain the 32-byte
     * unlock secret. The same (passkey, salt) pair on a new device yields
     * the same secret.
     */
    suspend fun deriveUnlockSecret(activity: Activity, prfSalt: ByteArray): ByteArray {
        if (prfSalt.size != RecoveryMetadata.PRF_SALT_SIZE) {
            throw PasskeyException("PRF salt must be 32 bytes")
        }
        val requestJson = JSONObject().apply {
            put("challenge", b64url(random32()))
            put("rpId", rpId)
            put("allowCredentials", org.json.JSONArray())
            put("extensions", JSONObject().put("prf", JSONObject()
                .put("eval", JSONObject().put("first", b64url(prfSalt)))))
        }
        val manager = CredentialManager.create(activity)
        val response = try {
            manager.getCredential(
                activity,
                androidx.credentials.GetCredentialRequest(
                    listOf(GetPublicKeyCredentialOption(requestJson.toString()))
                )
            )
        } catch (e: GetCredentialException) {
            throw PasskeyException("passkey confirmation failed", e,
                cancelled = e is androidx.credentials.exceptions.GetCredentialCancellationException)
        }
        val credential = response.credential as? PublicKeyCredential
            ?: throw PasskeyException("unexpected credential type")
        val secret = prfResult(credential.authenticationResponseJson)
            ?: throw PasskeyException("passkey does not support key derivation (PRF)")
        if (secret.size != RecoveryCode.SIZE) {
            throw PasskeyException("unexpected PRF output length")
        }
        return secret
    }

    // ── response parsing (platform-generated, well-formed JSON) ──────────

    private fun prfEnabledInRegistration(json: String): Boolean = try {
        val root = JSONObject(json)
        val extensions = root.optJSONObject("clientExtensionResults")
        extensions?.optJSONObject("prf")?.optBoolean("enabled", false) == true
    } catch (_: Exception) {
        false
    }

    private fun prfResult(json: String): ByteArray? = try {
        val root = JSONObject(json)
        val prf = root.optJSONObject("clientExtensionResults")?.optJSONObject("prf") ?: return null
        val results = prf.optJSONObject("results") ?: return null
        val first = results.optString("first", "")
        if (first.isEmpty()) null else b64urlDecode(first)
    } catch (_: Exception) {
        null
    }

    private fun random32(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    /** JWT-style base64 without padding, as WebAuthn requires. */
    private fun b64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun b64urlDecode(value: String): ByteArray =
        Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

/** Unsupported passkey providers may use a code; explicit cancellation must win. */
internal suspend fun passkeyEnrollmentOrFallback(create: suspend () -> Boolean): Boolean = try {
    create()
} catch (e: PasskeyBackupKeys.PasskeyException) {
    if (e.cancelled) throw e
    false
}
