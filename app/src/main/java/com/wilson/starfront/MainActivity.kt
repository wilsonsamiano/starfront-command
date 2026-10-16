package com.wilson.starfront

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

class MainActivity : Activity() {

    private lateinit var gameView: GameView
    private var updateDownloadId = -1L

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != -1L && id == updateDownloadId) {
                UpdateManager.installDownloadedApk(this@MainActivity, id)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        gameView = GameView(this)
        setContentView(gameView)

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(downloadReceiver, filter)
        }

        // Silent update check on launch; the info panel pops only if an update exists.
        UpdateManager.checkForUpdate { info ->
            if (info != null) runOnUiThread { gameView.showUpdateAvailable(info) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            // already unregistered
        }
    }

    fun startUpdateDownload(url: String) {
        updateDownloadId = UpdateManager.downloadUpdate(this, url)
    }

    override fun onPause() {
        super.onPause()
        gameView.pauseGame()
    }

    override fun onResume() {
        super.onResume()
        gameView.resumeGame()
    }
}
