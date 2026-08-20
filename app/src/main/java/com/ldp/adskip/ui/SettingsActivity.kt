package com.ldp.adskip.ui

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.ldp.adskip.R
import com.ldp.adskip.data.RulesRepository
import com.ldp.adskip.net.SyncClient
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

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        rulesRepo = RulesRepository(this)

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }
        etServer = findViewById(R.id.et_server)
        btnSync = findViewById(R.id.btn_sync)
        tvSyncResult = findViewById(R.id.tv_sync_result)
        tvLastSync = findViewById(R.id.tv_last_sync)

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
