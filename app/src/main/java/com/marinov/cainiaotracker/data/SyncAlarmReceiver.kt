package com.marinov.cainiaotracker.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.marinov.cainiaotracker.R
import com.marinov.cainiaotracker.ui.PackageActivity
import com.marinov.cainiaotracker.util.ActiveViewingManager
import com.marinov.cainiaotracker.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class SyncAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SYNC_CYCLE = "com.marinov.cainiaotracker.ACTION_SYNC_CYCLE"
        const val ACTION_CHECK_PACKAGE = "com.marinov.cainiaotracker.ACTION_CHECK_PACKAGE"
        const val EXTRA_TRACKING_CODE = "extra_tracking_code"
        const val EXTRA_RETRY_COUNT = "extra_retry_count"

        private const val TAG = "CainiaoSync"
        private const val CHANNEL_UPDATE = "package_update"

        // Margem abaixo do timeout de broadcast (~60 s em fila background)
        private const val CHECK_TIMEOUT_SECONDS = 50

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val checkMutex = Mutex()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_SYNC_CYCLE -> {
                Log.d(TAG, "Alarme de CICLO recebido")
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        SyncScheduler.runCycle(appContext)
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro no ciclo", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_CHECK_PACKAGE -> {
                val trackingCode = intent.getStringExtra(EXTRA_TRACKING_CODE) ?: return
                val retryCount = intent.getIntExtra(EXTRA_RETRY_COUNT, 0)
                Log.d(TAG, "Alarme de CHECK recebido: $trackingCode (retry=$retryCount)")
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        handleCheck(appContext, trackingCode, retryCount)
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro no check de $trackingCode", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private suspend fun handleCheck(context: Context, trackingCode: String, retryCount: Int) {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            val delay = SyncScheduler.offlineRetryDelay(retryCount)
            Log.d(TAG, "Sem rede → retry de '$trackingCode' em ${delay / 60000}min")
            SyncScheduler.schedulePackageCheck(context, trackingCode, delay, retryCount + 1)
            return
        }

        // Nunca espera segurando o broadcast: se ocupado, reagenda +30s
        // (isso também restaura o espaçamento de 30s quando o Doze agrupa alarmes)
        if (!checkMutex.tryLock()) {
            Log.d(TAG, "Outra verificação em andamento → '$trackingCode' em 30s")
            SyncScheduler.schedulePackageCheck(context, trackingCode, SyncScheduler.RETRY_BUSY_MS)
            return
        }

        try {
            val completed = withTimeoutOrNull(CHECK_TIMEOUT_SECONDS.seconds) {
                checkPackage(context, trackingCode)
            }
            if (completed == null) {
                Log.w(TAG, "Timeout de ${CHECK_TIMEOUT_SECONDS}s em '$trackingCode'")
            }
        } finally {
            checkMutex.unlock()
        }
    }

    private suspend fun checkPackage(context: Context, trackingCode: String) {
        if (ActiveViewingManager.isViewing(trackingCode)) {
            Log.d(TAG, "'$trackingCode' sendo visualizado agora → pulando")
            return
        }

        val repository = PackageRepository(context)
        val pkg = repository.getAllPackages()
            .firstOrNull { it.trackingCode == trackingCode && !it.isArchived }
        if (pkg == null) {
            Log.d(TAG, "'$trackingCode' removido/arquivado → pulando")
            return
        }

        val updateResult = GetTrackData.isAnyUpdate(context, trackingCode)
        Log.d(
            TAG,
            "Resultado '$trackingCode': hasUpdate=${updateResult.hasUpdate}, status='${updateResult.status}'" +
                    if (updateResult.status.isEmpty()) " (fetch falhou: erro/timeout/captcha)" else ""
        )

        if (updateResult.hasUpdate && updateResult.latestTitle.isNotEmpty()) {
            showUpdateNotification(context, pkg.name, updateResult.latestTitle, trackingCode)
        }

        // Auto-arquivamento discreto se entregue
        val deliveredStatuses = listOf("delivered", "妥投", "livré", "entregue")
        val isDelivered = deliveredStatuses.any {
            it.equals(updateResult.status.trim(), ignoreCase = true)
        }
        if (isDelivered) {
            val fresh = repository.getAllPackages().firstOrNull { it.id == pkg.id }
            if (fresh != null && !fresh.isArchived) {
                fresh.isArchived = true
                repository.updatePackage(fresh)
                Log.d(TAG, "'$trackingCode' entregue → arquivado")
            }
        }
    }

    private fun showUpdateNotification(
        context: Context,
        packageName: String,
        latestTitle: String,
        trackingCode: String
    ) {
        createUpdateChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "Update detectado, mas notificações estão DESABILITADAS para o app")
            return
        }

        val intent = Intent(context, PackageActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("trackingCode", trackingCode)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            trackingCode.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_box)
            .setContentTitle(context.getString(R.string.notification_update_title))
            .setContentText("$packageName: $latestTitle")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            manager.notify(trackingCode.hashCode(), notification)
            Log.d(TAG, "Notificação de update postada para '$trackingCode'")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException ao notificar", e)
        }
    }

    private fun createUpdateChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_UPDATE,
                context.getString(R.string.channel_package_update),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Updates" }
            manager.createNotificationChannel(channel)
        }
    }
}