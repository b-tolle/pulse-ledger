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
 * Self-update: reads latest.json from the repo, compares versionCode, and if
 * newer, downloads the APK and fires the system installer. One tap, no GitHub.
 */
object Updater {
    private const val MANIFEST =
        "https://raw.githubusercontent.com/b-tolle/pulse-ledger/main/latest.json"

    data class Available(val versionName: String, val apkUrl: String)

    suspend fun check(currentCode: Int): Available? = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(URL(MANIFEST).readText())
            if (json.getInt("versionCode") > currentCode)
                Available(json.getString("versionName"), json.getString("apk"))
            else null
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
