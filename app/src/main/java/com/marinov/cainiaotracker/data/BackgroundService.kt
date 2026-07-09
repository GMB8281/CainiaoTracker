package com.marinov.cainiaotracker.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.marinov.cainiaotracker.R
import com.marinov.cainiaotracker.ui.PackageActivity
import com.marinov.cainiaotracker.util.ActiveViewingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BackgroundService : Service() {

    companion object {
        var isRunning = false
        const val ACTION_UPDATE_INTERVAL = "com.marinov.cainiaotracker.ACTION_UPDATE_INTERVAL"
        const val EXTRA_INTERVAL = "extra_interval"
        private const val PREFS_NAME = "cainiao_settings"
        private const val KEY_SYNC_INTERVAL = "sync_interval_minutes"
        private const val DEFAULT_INTERVAL = 60L  // 1 hora
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentInterval = DEFAULT_INTERVAL
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val checkRunnable = object : Runnable {
        override fun run() {
            serviceScope.launch {
                checkForUpdates()
            }
            handler.postDelayed(this, currentInterval * 60 * 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannels()

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        currentInterval = prefs.getLong(KEY_SYNC_INTERVAL, DEFAULT_INTERVAL)

        startForegroundNotification()
        handler.post(checkRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE_INTERVAL) {
            val newInterval = intent.getLongExtra(EXTRA_INTERVAL, DEFAULT_INTERVAL)
            if (newInterval != currentInterval) {
                currentInterval = newInterval
                handler.removeCallbacks(checkRunnable)
                handler.post(checkRunnable)
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, "background_service")
            .setContentTitle(getString(R.string.notification_background_title))
            .setContentText(getString(R.string.notification_background_text))
            .setSmallIcon(R.drawable.ic_box)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun checkForUpdates() {
        val repository = PackageRepository(applicationContext)
        val packages = repository.getAllPackages().filter { !it.isArchived }

        for (pkg in packages) {
            try {
                // Pula encomendas que estão sendo visualizadas no momento
                if (ActiveViewingManager.isViewing(pkg.trackingCode)) {
                    continue
                }

                val updateResult = GetTrackData.isAnyUpdate(applicationContext, pkg.trackingCode)
                if (updateResult.hasUpdate && updateResult.latestTitle.isNotEmpty()) {
                    showUpdateNotification(pkg.name, updateResult.latestTitle, pkg.trackingCode)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showUpdateNotification(packageName: String, latestTitle: String, trackingCode: String) {
        val intent = Intent(this, PackageActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("trackingCode", trackingCode)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            trackingCode.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "package_update")
            .setSmallIcon(R.drawable.ic_box)
            .setContentTitle(getString(R.string.notification_update_title))
            .setContentText("$packageName: $latestTitle")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(trackingCode.hashCode(), notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(checkRunnable)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val channelService = NotificationChannel(
                "background_service",
                getString(R.string.channel_background_service),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Serviço" }

            val channelUpdate = NotificationChannel(
                "package_update",
                getString(R.string.channel_package_update),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Updates" }

            manager.createNotificationChannel(channelService)
            manager.createNotificationChannel(channelUpdate)
        }
    }
}