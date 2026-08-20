package com.ldp.adskip

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 云端规则同步设置：配置服务器地址、触发规则同步。
 */
class SettingsActivity : Activity() {

    private lateinit var etServer: EditText
    private lateinit var btnSync: Button
    private lateinit var tvSyncResult: TextView
    private lateinit var tvLastSync: TextView

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }
        etServer = findViewById(R.id.et_server)
        btnSync = findViewById(R.id.btn_sync)
        tvSyncResult = findViewById(R.id.tv_sync_result)
        tvLastSync = findViewById(R.id.tv_last_sync)

        etServer.setText(Prefs.getServerUrl(this))

        findViewById<Button>(R.id.btn_save_server).setOnClickListener {
            val url = etServer.text.toString().trim()
            if (!url.startsWith("http")) {
                Toast.makeText(this, "地址需以 http:// 开头", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.saveServerUrl(this, url)
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }

        btnSync.setOnClickListener {
            val url = etServer.text.toString().trim()
            Prefs.saveServerUrl(this, url)
            btnSync.isEnabled = false
            tvSyncResult.text = getString(R.string.settings_syncing)
            SyncUtil.syncRules(applicationContext, url) { ok, msg ->
                btnSync.isEnabled = true
                tvSyncResult.text = msg
                updateLastSync()
                Toast.makeText(this, msg, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
            }
        }

        updateLastSync()
    }

    private fun updateLastSync() {
        val ts = Prefs.getLastSyncAt(this)
        tvLastSync.text = if (ts > 0) {
            getString(R.string.settings_last_sync, timeFormat.format(Date(ts)))
        } else {
            getString(R.string.settings_never_sync)
        }
    }
}
