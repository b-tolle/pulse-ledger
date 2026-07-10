package com.example.pulseledger.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

/**
 * Self-update via the GitHub Releases API (always reflects the newest build,
 * no committed manifest to race). Release tags are "v0.1.<runNumber>", and the
 * runNumber is our versionCode, so we compare tag number to installed code.
 */
object Updater {
    private const val RELEASES =
        "https://api.github.com/repos/b-tolle/pulse-ledger/releases?per_page=1"
    private const val APK_URL =
        "https://github.com/b-tolle/pulse-ledger/releases/latest/download/pulse-ledger.apk"

    data class Available(val versionName: String, val apkUrl: String)

    suspend fun check(currentCode: Int): Available? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(RELEASES).openConnection() as java.net.HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 8000; readTimeout = 8000
            }
            // /releases returns newest-first; take [0] (— /releases/latest can return empty)
            val arr = org.json.JSONArray(conn.inputStream.bufferedReader().readText())
            if (arr.length() == 0) return@runCatching null
            val tag = arr.getJSONObject(0).getString("tag_name")   // e.g. "v0.1.41"
            val latestCode = tag.substringAfterLast('.').toIntOrNull() ?: return@runCatching null
            if (latestCode > currentCode) Available(tag.removePrefix("v"), APK_URL) else null
        }.getOrNull()
    }

    suspend fun downloadAndInstall(ctx: Context, apkUrl: String) = withContext(Dispatchers.IO) {
        val file = File(ctx.cacheDir, "update.apk")
        URL(apkUrl).openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        withContext(Dispatchers.Main) { ctx.startActivity(intent) }
    }
}
