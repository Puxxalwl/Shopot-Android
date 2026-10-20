package com.stille.messenger.backup

/**
 * A user-owned cloud location for the core-sealed recovery backup
 * (CLIENT_RULES.md §4: the user's own storage, no project-operated server).
 *
 * Implementations speak plain HTTPS to the provider; no SDKs. The blob is
 * opaque to the client — the core seals and opens it.
 */
interface BackupStore {
    /** The downloaded pair; the sidecar is null when only a legacy blob exists. */
    data class Downloaded(val blob: ByteArray, val sidecar: ByteArray?)

    /** Upload (create or replace) the sealed blob and its sidecar. Returns true when a previous copy was replaced. */
    fun upload(blob: ByteArray, sidecar: ByteArray): Boolean

    /** Download the blob + sidecar, or null when this storage has none. */
    fun download(): Downloaded?

    fun delete(): Boolean

    fun hasBackup(): Boolean
}

/** The storage the user chose for the backup copy. */
sealed class BackupTarget {
    data class GoogleDrive(val account: android.accounts.Account) : BackupTarget()
    data class WebDav(val credentials: WebDavCredentials) : BackupTarget()

    companion object {
        const val PROVIDER_GOOGLE = "google"
        const val PROVIDER_WEBDAV = "webdav"

        fun saveProvider(context: android.content.Context, provider: String) {
            context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .edit().putString(KEY_PROVIDER, provider).apply()
        }

        fun savedProvider(context: android.content.Context): String? =
            context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .getString(KEY_PROVIDER, null)

        private const val PREFS = "stille_backup"
        private const val KEY_PROVIDER = "backup_provider"
    }
}
