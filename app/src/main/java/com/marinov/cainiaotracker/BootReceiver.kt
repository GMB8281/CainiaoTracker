package com.marinov.cainiaotracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import com.marinov.cainiaotracker.data.SyncScheduler

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CainiaoSync"
        private const val BOOT_DELAY_MS = 30_000L

        // adb shell am broadcast -a com.marinov.cainiaotracker.DEBUG_SYNC_NOW \
        //   -n com.marinov.cainiaotracker/.BootReceiver
        const val ACTION_DEBUG_SYNC_NOW = "com.marinov.cainiaotracker.DEBUG_SYNC_NOW"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "Boot/update detectado → reagendando ciclo em ${BOOT_DELAY_MS / 1000}s")
                SyncScheduler.scheduleNextCycle(context, BOOT_DELAY_MS)
            }

            ACTION_DEBUG_SYNC_NOW -> {
                val debuggable =
                    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                if (debuggable) {
                    Log.d(TAG, "DEBUG_SYNC_NOW → ciclo imediato")
                    SyncScheduler.scheduleNextCycle(context, 0L)
                } else {
                    Log.w(TAG, "DEBUG_SYNC_NOW ignorado (build não-debuggable)")
                }
            }
        }
    }
}