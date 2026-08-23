package com.ldp.adskip.ui

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.ldp.adskip.AdskipApp
import com.ldp.adskip.data.Prefs
import com.ldp.adskip.R
import com.ldp.adskip.data.RulesRepository
import com.ldp.adskip.net.SyncClient
import com.ldp.adskip.sync.SyncJobService
import com.ldp.adskip.core.LogRing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 云端规则同步设置：配置服务器地址、触发规则同步。
 */
class SettingsActivity : Activity() {

    private lateinit var rulesRepo: RulesRepository
    private lateinit var etServer: EditText
    private lateinit var btnSync: Button
    private lateinit var tvSyncResult: TextView
    private lateinit var tvLastSync: TextView
    private lateinit var tvDndRange: TextView
    private lateinit var btnBattery: Button

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        rulesRepo = AdskipApp.get(this).rulesRepo

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }
        etServer = findViewById(R.id.et_server)
        btnSync = findViewById(R.id.btn_sync)
        tvSyncResult = findViewById(R.id.tv_sync_result)
        tvLastSync = findViewById(R.id.tv_last_sync)
        tvDndRange = findViewById(R.id.tv_dnd_range)
        btnBattery = findViewById(R.id.btn_battery)

        etServer.setText(SyncClient.serverUrl(this))

        findViewById<Button>(R.id.btn_save_server).setOnClickListener {
            val url = etServer.text.toString().trim()
            if (!isValidServerUrl(url)) {
                Toast.makeText(this, "地址需为 http:// 或 https:// 开头的有效地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            SyncClient.saveServerUrl(this, url)
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }

        btnSync.setOnClickListener {
            val url = etServer.text.toString().trim()
            if (!isValidServerUrl(url)) {
                Toast.makeText(this, "地址需为 http:// 或 https:// 开头的有效地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            SyncClient.saveServerUrl(this, url)
            btnSync.isEnabled = false
            tvSyncResult.text = getString(R.string.settings_syncing)
            SyncClient.syncRules(applicationContext, url, rulesRepo) { ok, msg ->
                if (isFinishing || isDestroyed) return@syncRules
                btnSync.isEnabled = true
                tvSyncResult.text = msg
                updateLastSync()
                Toast.makeText(this, msg, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
            }
        }

        updateLastSync()
        val autoSync = findViewById<Switch>(R.id.switch_auto_sync)
        autoSync.isChecked = Prefs.isAutoSyncEnabled(this)
        autoSync.setOnCheckedChangeListener { _, enabled -> SyncJobService.setEnabled(this, enabled) }

        val dnd = findViewById<Switch>(R.id.switch_dnd)
        dnd.isChecked = Prefs.isDoNotDisturbEnabled(this)
        dnd.setOnCheckedChangeListener { _, enabled -> Prefs.setDoNotDisturbEnabled(this, enabled) }
        findViewById<Button>(R.id.btn_dnd_start).setOnClickListener { pickDndTime(true) }
        findViewById<Button>(R.id.btn_dnd_end).setOnClickListener { pickDndTime(false) }
        updateDndRange()
        btnBattery.setOnClickListener { openBatterySettings() }
        updateBatteryStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::btnBattery.isInitialized) updateBatteryStatus()
    }

    private fun pickDndTime(start: Boolean) {
        val minute = if (start) Prefs.getDoNotDisturbStart(this) else Prefs.getDoNotDisturbEnd(this)
        TimePickerDialog(this, { _, hour, selectedMinute ->
            val startMinute = if (start) hour * 60 + selectedMinute else Prefs.getDoNotDisturbStart(this)
            val endMinute = if (start) Prefs.getDoNotDisturbEnd(this) else hour * 60 + selectedMinute
            Prefs.setDoNotDisturbTimes(this, startMinute, endMinute)
            updateDndRange()
        }, minute / 60, minute % 60, true).show()
    }

    private fun updateDndRange() {
        fun format(minute: Int) = "%02d:%02d".format(Locale.getDefault(), minute / 60, minute % 60)
        tvDndRange.text = "${format(Prefs.getDoNotDisturbStart(this))} - ${format(Prefs.getDoNotDisturbEnd(this))}"
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun updateBatteryStatus() {
        val powerManager = getSystemService(PowerManager::class.java)
        val allowed = powerManager.isIgnoringBatteryOptimizations(packageName)
        btnBattery.text = getString(if (allowed) R.string.settings_battery_done else R.string.settings_battery_allow)
    }

    private fun updateLastSync() {
        val ts = SyncClient.lastSyncAt(this)
        tvLastSync.text = if (ts > 0) {
            getString(R.string.settings_last_sync, timeFormat.format(Date(ts)))
        } else {
            getString(R.string.settings_never_sync)
        }
    }

    private fun isValidServerUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }
}
