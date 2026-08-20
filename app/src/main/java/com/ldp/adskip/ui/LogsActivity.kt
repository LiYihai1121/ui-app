package com.ldp.adskip.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.ldp.adskip.R
import com.ldp.adskip.data.StatsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 跳过日志：展示最近 200 条自动跳过记录。
 */
class LogsActivity : Activity() {

    private lateinit var statsRepo: StatsRepository
    private lateinit var llLogs: LinearLayout
    private lateinit var tvEmpty: TextView

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        statsRepo = StatsRepository(this)

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }
        llLogs = findViewById(R.id.ll_logs)
        tvEmpty = findViewById(R.id.tv_empty)

        findViewById<Button>(R.id.btn_clear).setOnClickListener {
            statsRepo.clearLogs()
            refresh()
            Toast.makeText(this, R.string.logs_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun refresh() {
        llLogs.removeAllViews()
        val logs = statsRepo.logs()
        tvEmpty.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        for (entry in logs) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            row.addView(TextView(this).apply {
                text = entry.label
                textSize = 15f
                setTextColor(getColor(R.color.text_primary))
            })
            row.addView(TextView(this).apply {
                text = timeFormat.format(Date(entry.ts)) + " ｜ " + entry.pkg
                textSize = 12f
                setTextColor(getColor(R.color.text_secondary))
            })
            llLogs.addView(row)
        }
    }
}
