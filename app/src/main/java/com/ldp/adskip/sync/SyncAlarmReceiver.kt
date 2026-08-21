package com.ldp.adskip.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ldp.adskip.data.Prefs
import com.ldp.adskip.data.RulesRepository
import com.ldp.adskip.net.SyncClient

class SyncAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!Prefs.isAutoSyncEnabled(context)) return
        val pending = goAsync()
        val appContext = context.applicationContext
        SyncClient.syncRules(appContext, Prefs.getServerUrl(appContext), RulesRepository(appContext)) { _, _ ->
            pending.finish()
        }
    }
}