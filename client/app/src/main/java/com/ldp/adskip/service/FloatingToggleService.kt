package com.ldp.adskip.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import com.ldp.adskip.R
import com.ldp.adskip.data.Prefs

/**
 * 悬浮窗快捷开关（ROADMAP「悬浮窗快捷开关」）。
 *
 * 在任意应用上层显示一个小面板，一键完成最常用的两个操作——
 * 暂停/恢复自动跳过、切换免打扰——不用来回切换应用或反复开停无障碍服务。
 *
 * 实现要点：
 * - 悬浮层用 `TYPE_APPLICATION_OVERLAY`（需用户显式授予「显示在其他应用上层」）；
 * - 长驻悬浮层由前台服务承载（Android 8+ 后台启动限制），通知常驻但可进任务管理隐藏；
 * - 「暂停」只写偏好（[Prefs.setPaused]），服务照常运行——与开停无障碍服务等效但零系统弹窗；
 * - 组件不导出（manifest `exported=false`），只有本应用能启停。
 */
class FloatingToggleService : Service() {

    private var windowManager: WindowManager? = null
    private var panelView: View? = null
    private lateinit var pauseButton: Button
    private lateinit var dndButton: Button

    override fun onBind(intent: Intent?): Nothing? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        startInForeground()
        showPanel()
    }

    override fun onDestroy() {
        running = false
        panelView?.let { runCatching { windowManager?.removeView(it) } }
        panelView = null
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_skip)
            .setContentTitle(getString(R.string.overlay_notif_title))
            .setContentText(getString(R.string.overlay_notif_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun showPanel() {
        val wm = getSystemService(WindowManager::class.java)
        windowManager = wm

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC1F2937"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        pauseButton = buildButton().apply {
            setOnClickListener { togglePause() }
        }
        dndButton = buildButton().apply {
            setOnClickListener { toggleDnd() }
        }
        val pickButton = buildButton().apply {
            setText(R.string.overlay_btn_pick)
            // 取点模式：盖一层透明层在广告界面上直接标注「跳过」位置
            setOnClickListener { PointPickService.start(this@FloatingToggleService) }
        }
        val closeButton = buildButton().apply {
            setText(R.string.overlay_btn_close)
            setOnClickListener { stopSelf() }
        }

        panel.addView(pauseButton)
        panel.addView(dndButton)
        panel.addView(pickButton)
        panel.addView(closeButton)
        refreshLabels()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 不可获焦：悬浮层不抢目标应用的输入焦点，只响应自身按钮
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(96)
        }
        wm.addView(panel, params)
        panelView = panel
    }

    private fun buildButton(): Button = Button(this).apply {
        textSize = 12f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#33FFFFFF"))
        minWidth = dp(120)
    }

    private fun togglePause() {
        Prefs.setPaused(this, !Prefs.isPaused(this))
        refreshLabels()
    }

    private fun toggleDnd() {
        Prefs.setDoNotDisturbEnabled(this, !Prefs.isDoNotDisturbEnabled(this))
        refreshLabels()
    }

    private fun refreshLabels() {
        pauseButton.setText(if (Prefs.isPaused(this)) R.string.overlay_btn_resume else R.string.overlay_btn_pause)
        dndButton.setText(
            if (Prefs.isDoNotDisturbEnabled(this)) R.string.overlay_btn_dnd_on else R.string.overlay_btn_dnd_off,
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHANNEL_ID = "floating_toggle"
        private const val NOTIF_ID = 42

        /** 是否已显示悬浮开关（由服务生命周期维护，系统杀进程后自动复位） */
        @Volatile
        var running = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingToggleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingToggleService::class.java))
        }
    }
}
