package com.ldp.adskip

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvStatusHint: TextView
    private lateinit var tvStats: TextView
    private lateinit var etKeyword: EditText
    private lateinit var llKeywords: LinearLayout
    private lateinit var overlay: View
    private lateinit var btnFakeSkip: Button
    private lateinit var tvCountdown: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var countdown = 5
    private val countdownTask = object : Runnable {
        override fun run() {
            countdown--
            if (countdown > 0) {
                tvCountdown.text = getString(R.string.fake_ad_countdown, countdown)
                handler.postDelayed(this, 1000)
            } else {
                endTest(success = false)
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                SkipAdService.ACTION_SKIPPED -> {
                    val pkg = intent.getStringExtra(SkipAdService.EXTRA_PKG) ?: ""
                    if (SkipAdService.testActive) {
                        endTest(success = true, pkg = pkg)
                    }
                    updateStats()
                }
                SkipAdService.ACTION_SERVICE_STATE -> updateStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_status)
        tvStatusHint = findViewById(R.id.tv_status_hint)
        tvStats = findViewById(R.id.tv_stats)
        etKeyword = findViewById(R.id.et_keyword)
        llKeywords = findViewById(R.id.ll_keywords)
        overlay = findViewById(R.id.overlay)
        btnFakeSkip = findViewById(R.id.btn_fake_skip)
        tvCountdown = findViewById(R.id.tv_countdown)

        findViewById<Button>(R.id.btn_open_settings).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btn_test).setOnClickListener { startTest() }

        findViewById<Button>(R.id.btn_apps).setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }
        findViewById<Button>(R.id.btn_logs).setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btn_add_keyword).setOnClickListener { addKeyword() }
        etKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addKeyword()
                true
            } else false
        }

        // 点击关键词即删除
        // （在 refreshKeywords 中为每一项设置点击事件）

        btnFakeSkip.setOnClickListener {
            // 手动点击视为服务未生效
            endTest(success = false, manual = true)
        }

        overlay.setOnClickListener {
            // 防止误触穿透，测试中不允许点空白关闭
        }

        refreshKeywords()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(SkipAdService.ACTION_SKIPPED)
            addAction(SkipAdService.ACTION_SERVICE_STATE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        updateStatus()
        updateStats()
        refreshKeywords()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    private fun updateStatus() {
        if (SkipAdService.running) {
            tvStatus.setText(R.string.status_on)
            tvStatus.setTextColor(getColor(R.color.status_on))
            tvStatusHint.setText(R.string.status_on_hint)
        } else {
            tvStatus.setText(R.string.status_off)
            tvStatus.setTextColor(getColor(R.color.status_off))
            tvStatusHint.setText(R.string.status_off_hint)
        }
    }

    private fun updateStats() {
        val total = Prefs.getTotalSkips(this)
        val last = Prefs.getLastApp(this)
        tvStats.text = if (last.isEmpty()) {
            getString(R.string.stats_format, total, getString(R.string.stats_none))
        } else {
            getString(R.string.stats_format, total, last)
        }
    }

    private fun refreshKeywords() {
        llKeywords.removeAllViews()
        val list = Prefs.getKeywords(this)
        for ((index, kw) in list.withIndex()) {
            val item = layoutInflater.inflate(R.layout.item_keyword, llKeywords, false) as TextView
            item.text = getString(R.string.keyword_item, kw)
            item.setOnClickListener {
                val current = Prefs.getKeywords(this)
                if (index < current.size) {
                    val removed = current.removeAt(index)
                    Prefs.saveKeywords(this, current)
                    refreshKeywords()
                    Toast.makeText(this, getString(R.string.keyword_removed, removed), Toast.LENGTH_SHORT).show()
                }
            }
            llKeywords.addView(item)
        }
    }

    private fun addKeyword() {
        val kw = etKeyword.text.toString().trim()
        if (kw.isEmpty()) {
            Toast.makeText(this, R.string.keyword_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val list = Prefs.getKeywords(this)
        if (list.any { it.equals(kw, ignoreCase = true) }) {
            Toast.makeText(this, R.string.keyword_exists, Toast.LENGTH_SHORT).show()
            return
        }
        list.add(kw)
        Prefs.saveKeywords(this, list)
        etKeyword.text.clear()
        refreshKeywords()
        Toast.makeText(this, getString(R.string.keyword_added, kw), Toast.LENGTH_SHORT).show()
    }

    /** 模拟一个带"跳过"按钮的开屏广告，用于验证无障碍服务是否生效。 */
    private fun startTest() {
        if (!SkipAdService.running) {
            Toast.makeText(this, R.string.test_need_service, Toast.LENGTH_LONG).show()
        }
        SkipAdService.testActive = true
        countdown = 5
        tvCountdown.text = getString(R.string.fake_ad_countdown, countdown)
        overlay.visibility = View.VISIBLE
        handler.postDelayed(countdownTask, 1000)
    }

    private fun endTest(success: Boolean, pkg: String? = null, manual: Boolean = false) {
        handler.removeCallbacks(countdownTask)
        SkipAdService.testActive = false
        overlay.visibility = View.GONE
        when {
            success -> Toast.makeText(
                this,
                getString(R.string.test_success, pkg ?: ""),
                Toast.LENGTH_LONG
            ).show()
            manual -> Toast.makeText(this, R.string.test_manual, Toast.LENGTH_LONG).show()
            else -> Toast.makeText(this, R.string.test_timeout, Toast.LENGTH_LONG).show()
        }
    }
}
