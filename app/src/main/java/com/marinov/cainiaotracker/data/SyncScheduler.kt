package com.marinov.cainiaotracker.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit

object SyncScheduler {

    private const val TAG = "CainiaoSync"

    private const val PREFS_NAME = "cainiao_settings"
    private const val KEY_SYNC_INTERVAL = "sync_interval_minutes"
    private const val KEY_NEXT_CYCLE_AT = "next_cycle_at"

    const val DEFAULT_INTERVAL = 60L // 1 hora

    private const val REQUEST_CYCLE = 1001
    private const val SPACING_MS = 30_000L         // 30 s entre encomendas (espaçamento mínimo)
    private const val GROUP_SIZE = 3
    private const val GROUP_PAUSE_MS = 5 * 60_000L // 5 min de pausa a cada 3

    const val RETRY_BUSY_MS = 30_000L
    private const val RETRY_OFFLINE_MS = 5 * 60_000L
    private const val RETRY_OFFLINE_MAX_MS = 60 * 60_000L

    // false = ELAPSED_REALTIME: conta desde o boot, imune a mudança de hora (recomendado p/ produção)
    // true  = RTC: segue o relógio de parede — útil apenas para testes de adiantar o relógio
    private const val USE_RTC = false

    private fun now(): Long =
        if (USE_RTC) System.currentTimeMillis() else SystemClock.elapsedRealtime()

    private fun alarmType(): Int =
        if (USE_RTC) AlarmManager.RTC_WAKEUP else AlarmManager.ELAPSED_REALTIME_WAKEUP

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getIntervalMinutes(context: Context): Long =
        prefs(context).getLong(KEY_SYNC_INTERVAL, DEFAULT_INTERVAL)

    /** Restaura/garante o ciclo preservando o tempo restante (app reaberto, pós force-stop, etc.) */
    fun ensureScheduled(context: Context) {
        val next = prefs(context).getLong(KEY_NEXT_CYCLE_AT, 0L)
        val remaining = (next - now()).coerceAtLeast(0L)
        Log.d(TAG, "ensureScheduled: faltam ${remaining / 1000}s para o próximo ciclo")
        scheduleNextCycle(context, remaining)
    }

    /** Pacote adicionado ou reativado: verificação imediata + garante o ciclo recorrente. */
    fun onPackageAdded(context: Context, trackingCode: String) {
        Log.d(TAG, "Pacote adicionado/reativado: $trackingCode → check em 2s")
        schedulePackageCheck(context, trackingCode, 2_000L)
        ensureScheduled(context)
    }

    fun updateInterval(context: Context, minutes: Long) {
        prefs(context).edit { putLong(KEY_SYNC_INTERVAL, minutes) }
        Log.d(TAG, "Intervalo alterado para ${minutes}min")
        scheduleNextCycle(context, minutes * 60_000L)
    }

    fun scheduleNextCycle(context: Context, delayMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = cyclePendingIntent(context)
        am.cancel(pi)
        val triggerAt = now() + delayMillis
        scheduleAlarm(am, triggerAt, pi)
        prefs(context).edit { putLong(KEY_NEXT_CYCLE_AT, triggerAt) }
        Log.d(TAG, "CICLO agendado: +${delayMillis / 1000}s (trigger=$triggerAt, base=${if (USE_RTC) "RTC" else "ELAPSED"})")
    }

    /** Disparado pelo alarme de ciclo: 1 alarme por encomenda, escalonado como no serviço antigo. */
    fun runCycle(context: Context) {
        val packages = PackageRepository(context).getAllPackages().filter { !it.isArchived }
        Log.d(TAG, "CICLO disparado: ${packages.size} pacote(s) ativo(s)")
        var lastOffset = 0L
        packages.forEachIndexed { index, pkg ->
            // Offsets: 0s, 30s, 60s, 6min, 6min30s, 7min, 12min, ...
            val offset = index * SPACING_MS + (index / GROUP_SIZE) * (GROUP_PAUSE_MS - SPACING_MS)
            lastOffset = offset
            schedulePackageCheck(context, pkg.trackingCode, offset)
        }
        val intervalMs = getIntervalMinutes(context) * 60_000L
        scheduleNextCycle(context, lastOffset + intervalMs)
    }

    fun schedulePackageCheck(
        context: Context,
        trackingCode: String,
        delayMillis: Long,
        retryCount: Int = 0
    ) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = packagePendingIntent(context, trackingCode, retryCount)
        am.cancel(pi)
        val triggerAt = now() + delayMillis
        scheduleAlarm(am, triggerAt, pi)
        Log.d(TAG, "CHECK '$trackingCode' agendado: +${delayMillis / 1000}s (retry=$retryCount, trigger=$triggerAt)")
    }

    /** Backoff offline: 5, 10, 20, 40, 60, 60... minutos */
    fun offlineRetryDelay(retryCount: Int): Long {
        val factor = 1L shl retryCount.coerceIn(0, 4)
        return (RETRY_OFFLINE_MS * factor).coerceAtMost(RETRY_OFFLINE_MAX_MS)
    }

    /**
     * Sem permissões de alarme exato (política do Play):
     *  - Android 12+ (API 31+): inexact com setAndAllowWhileIdle (ainda dispara em Doze).
     *  - Android 6–11 (API 23–30): exato não exige permissão → timing preciso mantido.
     * O receiver serializa via tryLock + retry, então o agrupamento de alarmes inexact
     * não causa verificações simultâneas nem tempestade de captcha.
     */
    private fun scheduleAlarm(am: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                am.setAndAllowWhileIdle(alarmType(), triggerAt, pi)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                am.setExactAndAllowWhileIdle(alarmType(), triggerAt, pi)
            }
            else -> {
                am.setExact(alarmType(), triggerAt, pi)
            }
        }
    }

    private fun cyclePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SyncAlarmReceiver::class.java).apply {
            action = SyncAlarmReceiver.ACTION_SYNC_CYCLE
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CYCLE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun packagePendingIntent(
        context: Context,
        trackingCode: String,
        retryCount: Int
    ): PendingIntent {
        val intent = Intent(context, SyncAlarmReceiver::class.java).apply {
            action = SyncAlarmReceiver.ACTION_CHECK_PACKAGE
            putExtra(SyncAlarmReceiver.EXTRA_TRACKING_CODE, trackingCode)
            putExtra(SyncAlarmReceiver.EXTRA_RETRY_COUNT, retryCount)
        }
        return PendingIntent.getBroadcast(
            context,
            trackingCode.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}