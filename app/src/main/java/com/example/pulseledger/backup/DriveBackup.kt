package com.example.pulseledger.backup

/**
 * Optional encrypted backup of the Room database to Google Drive's
 * hidden appDataFolder (visible only to this app, not your Drive UI).
 *
 * Recommended flow:
 *  1. Sign in with Google (DriveScopes.DRIVE_APPDATA only).
 *  2. Encrypt a copy of pulse.db with a key in Android Keystore
 *     (e.g. androidx.security:security-crypto EncryptedFile).
 *  3. drive.files().create() into "appDataFolder", keep last N snapshots.
 *
 * Left as a stub so the app compiles without Google Cloud project setup —
 * you'll need an OAuth client ID in the Google Cloud console to enable it.
 */
object DriveBackup {
    fun isConfigured(): Boolean = false
}
