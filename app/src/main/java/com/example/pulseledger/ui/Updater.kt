package com.example.pulseledger.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-update WITHOUT the GitHub API (anonymous API is rate-limited to 60/hr,
 * which silently breaks update checks). Instead we resolve the "latest release"
 * redirect: hitting /releases/latest returns a 302 to /releases/tag/vX.Y.Z, and
 * the tag's last number is our versionCode. The APK is a fixed download path.
 */
object Updater {
    private const val LATEST_REDIRECT =
        "https://github.com/b-tolle/pulse-ledger/releases/latest"
    private const val APK_URL =
        "https://github.com/b-tolle/pulse-ledger/releases/latest/download/pulse-ledger.apk"

    data class Available(val versionName: String, val apkUrl: String)

    suspend fun check(currentCode: Int): Available? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(LATEST_REDIRECT).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false     // we want to READ the redirect target
                connectTimeout = 8000; readTimeout = 8000
                requestMethod = "HEAD"
            }
            val loc = conn.getHeaderField("Location") ?: return@runCatching null
            // loc = ".../releases/tag/v0.1.42"
            val tag = loc.substringAfterLast("/tag/").ifBlank { return@runCatching null }
            val latestCode = tag.substringAfterLast('.').toIntOrNull() ?: return@runCatching null
            if (latestCode > currentCode) Available(tag.removePrefix("v"), APK_URL) else null
        }.getOrNull()
    }

    suspend fun downloadAndInstall(ctx: Context, apkUrl: String) = withContext(Dispatchers.IO) {
        val file = File(ctx.cacheDir, "update.apk")
        (URL(apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true; connectTimeout = 15000; readTimeout = 60000
        }.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        withContext(Dispatchers.Main) { ctx.startActivity(intent) }
    }
}
