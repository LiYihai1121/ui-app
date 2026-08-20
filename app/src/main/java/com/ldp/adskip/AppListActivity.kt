package com.ldp.adskip

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/**
 * 应用管理：列出所有可启动应用，支持逐项开启/关闭自动跳过，显示各应用跳过次数。
 */
class AppListActivity : Activity() {

    private lateinit var llApps: LinearLayout
    private lateinit var tvLoading: TextView

    private data class AppItem(val pkg: String, val label: String, val icon: Drawable?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }
        llApps = findViewById(R.id.ll_apps)
        tvLoading = findViewById(R.id.tv_loading)

        loadApps()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val items = pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map {
                    AppItem(
                        pkg = it.activityInfo.packageName,
                        label = it.loadLabel(pm).toString(),
                        icon = try { it.loadIcon(pm) } catch (e: Exception) { null }
                    )
                }
                .sortedBy { it.label.lowercase() }
            runOnUiThread { render(items) }
        }.start()
    }

    private fun render(items: List<AppItem>) {
        tvLoading.visibility = View.GONE
        llApps.removeAllViews()
        for (item in items) {
            val disabled = Prefs.isPackageDisabled(this, item.pkg)
            val count = Prefs.getPkgSkipCount(this, item.pkg)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }

            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                scaleType = ImageView.ScaleType.FIT_CENTER
                item.icon?.let { setImageDrawable(it) }
            }
            row.addView(icon)

            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                }
            }
            texts.addView(TextView(this).apply {
                text = item.label
                textSize = 15f
                setTextColor(getColor(R.color.text_primary))
            })
            texts.addView(TextView(this).apply {
                text = when {
                    disabled -> getString(R.string.apps_disabled)
                    count > 0 -> getString(R.string.apps_count, count)
                    else -> getString(R.string.apps_never)
                }
                textSize = 12f
                setTextColor(getColor(R.color.text_secondary))
            })
            row.addView(texts)

            val toggle = Switch(this).apply {
                isChecked = !disabled
                setOnCheckedChangeListener { _, isChecked ->
                    Prefs.setPackageDisabled(this@AppListActivity, item.pkg, !isChecked)
                    // 刷新副标题
                    (texts.getChildAt(1) as TextView).text = when {
                        !isChecked -> getString(R.string.apps_disabled)
                        count > 0 -> getString(R.string.apps_count, count)
                        else -> getString(R.string.apps_never)
                    }
                }
            }
            row.addView(toggle)

            llApps.addView(row)
        }
    }
}
