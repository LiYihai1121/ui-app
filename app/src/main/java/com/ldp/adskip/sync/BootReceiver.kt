package com.ldp.adskip.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ldp.adskip.data.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && Prefs.isAutoSyncEnabled(context)) {
            SyncScheduler.schedule(context)
        }
    }
}