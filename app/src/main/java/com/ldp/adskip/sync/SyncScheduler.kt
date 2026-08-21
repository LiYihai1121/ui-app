package com.ldp.adskip.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ldp.adskip.data.Prefs

object SyncScheduler {
    private const val REQUEST_CODE = 2101
    private const val INTERVAL_MS = 12 * 60 * 60 * 1000L

    fun setEnabled(context: Context, enabled: Boolean) {
        Prefs.setAutoSyncEnabled(context, enabled)
        if (enabled) schedule(context) else cancel(context)
    }

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 60_000L,
            INTERVAL_MS,
            pendingIntent(context)
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, SyncAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}