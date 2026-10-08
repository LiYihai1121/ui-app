package com.ldp.adskip.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.ldp.adskip.R
import com.ldp.adskip.data.PointRules
import com.ldp.adskip.engine.SafetyGuard

/**
 * 取点模式（ROADMAP「截屏取点自定义规则」的落地形态）。
 *
 * 全屏透明悬浮层盖在目标广告界面上，用户**直接在真实界面上点**「跳过」按钮
 * 的位置——与「截屏后在图上标注」目标一致，但不需要录屏/截屏权限与授权弹窗，
 * 且所见即所得。落点按屏幕比例保存（见 [PointRules]），仅存本机。
 *
 * 安全边界：目标应用包名取自无障碍服务的最近前台窗口（[SkipAdService.lastActivePkg]），
 * 本应用自身与敏感系统界面（授权/安装/设置）不允许取点——坐标点击也必须过
 * [SafetyGuard.canClickPackage] 的包级护栏，与节点点击同一份硬底线。
 */
class PointPickService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): Nothing? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        startInForeground()
        showOverlay()
    }

    override fun onDestroy() {
        running = false
        overlayView?.let { runCatching { windowManager?.removeView(it) } }
        overlayView = null
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.point_pick_channel_name),
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
            .setContentTitle(getString(R.string.point_pick_notif_title))
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

    private fun showOverlay() {
        val pkg = currentTargetPackage()
        if (pkg == null) {
            Toast.makeText(this, R.string.point_pick_no_pkg, Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        val wm = getSystemService(WindowManager::class.java)
        windowManager = wm

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#55000000"))
        }

        val hint = TextView(this).apply {
            text = getString(R.string.point_pick_hint) + "\n" + getString(R.string.point_pick_current, pkg)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC1F2937"))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val cancel = Button(this).apply {
            setText(R.string.point_pick_cancel)
            // 触控目标下限（联合厂商无障碍/适老化基线 48dp）
            minHeight = dp(48)
            setOnClickListener { stopSelf() }
        }
        val cancelBar = LinearLayout(this@PointPickService).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#CC1F2937"))
            addView(cancel)
        }

        root.addView(
            hint,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ),
        )
        root.addView(
            cancelBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )

        // 整层可点：点哪存哪（子按钮先消费自身区域的触摸）
        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                savePoint(pkg, event.rawX, event.rawY)
            }
            true
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            0,
            PixelFormat.TRANSLUCENT,
        )
        wm.addView(root, params)
        overlayView = root
    }

    /** 目标包名 = 无障碍服务记录的最近前台应用；本应用/敏感系统界面不允许取点 */
    private fun currentTargetPackage(): String? {
        val pkg = SkipAdService.lastActivePkg
        if (pkg.isNullOrBlank() || !SafetyGuard.canClickPackage(pkg)) return null
        return pkg
    }

    private fun savePoint(pkg: String, rawX: Float, rawY: Float) {
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        if (width <= 0 || height <= 0) {
            Toast.makeText(this, R.string.point_pick_no_pkg, Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }
        PointRules.set(this, pkg, rawX / width, rawY / height)
        Toast.makeText(this, getString(R.string.point_pick_saved, pkg), Toast.LENGTH_SHORT).show()
        stopSelf()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHANNEL_ID = "point_pick"
        private const val NOTIF_ID = 43

        /** 取点模式是否在显示 */
        @Volatile
        var running = false
            private set

        fun start(context: android.content.Context) {
            val intent = Intent(context, PointPickService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, PointPickService::class.java))
        }
    }
}
