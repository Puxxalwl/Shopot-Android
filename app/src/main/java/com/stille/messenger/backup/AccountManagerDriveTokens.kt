package com.stille.messenger.backup

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerFuture
import android.os.Bundle
import android.content.Context

/**
 * Drive OAuth tokens from the on-device Google account chosen by the user.
 * Uses the platform AccountManager — no Google SDK, no bundled client id.
 * The `drive.appdata` scope only grants access to the hidden app folder
 * where the encrypted backup lives.
 */
class AccountManagerDriveTokens(
    context: Context,
    private val account: Account,
    private val activity: android.app.Activity? = null
) : DriveTokenProvider {

    private val accountManager = AccountManager.get(context.applicationContext)

    class TokenException(message: String) : Exception(message)

    override fun accessToken(): String {
        // The Activity overload lets Android show Google's consent UI. Never
        // block the main thread waiting for the result (callers use Dispatchers.IO).
        val future: AccountManagerFuture<Bundle> = if (activity != null) {
            accountManager.getAuthToken(
                account, "oauth2:${DriveBackupStore.scope()}", Bundle(), activity, null, null
            )
        } else {
            accountManager.getAuthToken(
                account, "oauth2:${DriveBackupStore.scope()}", Bundle(), false, null, null
            )
        }
        val bundle = try {
            future.result
        } catch (e: Exception) {
            throw TokenException("Google account authorization failed: ${e.javaClass.simpleName}")
        }
        val intent = bundle.get(AccountManager.KEY_INTENT)
        if (intent is android.content.Intent) {
            // Consent requires an interactive launch the caller must handle.
            throw TokenException("Google account consent required")
        }
        val token = bundle.getString(AccountManager.KEY_AUTHTOKEN)
        if (token.isNullOrBlank()) throw TokenException("empty Drive token")
        return token
    }

    override fun invalidate(token: String) {
        accountManager.invalidateAuthToken(GOOGLE_ACCOUNT_TYPE, token)
    }

    companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"

        /** The account the user picked for backup storage, remembered locally. */
        fun savedAccountName(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ACCOUNT, null)

        fun saveAccountName(context: Context, name: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_ACCOUNT, name).apply()
        }

        fun clearAccountName(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_ACCOUNT).apply()
        }

        fun accountByName(context: Context, name: String): Account? =
            AccountManager.get(context.applicationContext)
                .getAccountsByType(GOOGLE_ACCOUNT_TYPE)
                .firstOrNull { it.name == name }

        /** The saved account object, if still present on this device. */
        fun savedAccount(context: Context): Account? {
            val name = savedAccountName(context) ?: return null
            return accountByName(context, name)
        }

        private const val PREFS = "stille_backup"
        private const val KEY_ACCOUNT = "drive_account"
    }
}
