package com.wilson.starfront

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AppLinks {
    const val BMC_URL = "https://buymeacoffee.com/wilsonsamiano"
    const val RELEASES_API = "https://api.github.com/repos/wilsonsamiano/starfront-command/releases/latest"
}

/** Info about an available update. Callbacks run on a background thread. */
data class UpdateInfo(val tag: String, val versionCode: Int, val apkUrl: String)

object UpdateManager {

    /**
     * Checks GitHub releases for a newer build. Release tags must look like
     * "vN" where N is the versionCode (e.g. tag "v3" > installed versionCode 2).
     * Calls back with UpdateInfo, or null when up to date / offline / no releases.
     */
    fun checkForUpdate(onResult: (UpdateInfo?) -> Unit) {
        Thread {
            try {
                val conn = URL(AppLinks.RELEASES_API).openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                if (conn.responseCode != 200) {
                    onResult(null)
                    return@Thread
                }
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val tag = json.optString("tag_name", "")
                val latestCode = tag.filter { it.isDigit() }.toIntOrNull() ?: 0
                if (latestCode <= BuildConfig.VERSION_CODE) {
                    onResult(null)
                    return@Thread
                }
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name", "").endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", null)
                                .takeIf { it.isNotEmpty() }
                            break
                        }
                    }
                }
                onResult(if (apkUrl != null) UpdateInfo(tag, latestCode, apkUrl) else null)
            } catch (e: Exception) {
                onResult(null)
            }
        }.start()
    }

    /** Downloads the APK via DownloadManager. Returns the download id. */
    fun downloadUpdate(context: Context, url: String): Long {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Starfront Command update")
            .setDescription("Downloading new version…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "starfront-update.apk")
            .setMimeType("application/vnd.android.package-archive")
        return dm.enqueue(request)
    }

    /** Fires the package installer for a finished DownloadManager download. */
    fun installDownloadedApk(context: Context, downloadId: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri: Uri = dm.getUriForDownloadedFile(downloadId) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
