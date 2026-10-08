package com.ldp.adskip.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import com.ldp.adskip.core.LogRing

/**
 * 本地广播接收器生命周期句柄（FOLLOW-UP：`SkipAdService` 接收器注册/注销模板重复）。
 *
 * 收敛两个接收器共享的公共路径：
 * - Android 13+ 必须 `RECEIVER_NOT_EXPORTED`，旧版本仅两参重载（API 分支只写一次）；
 * - `unregisterReceiver` 抛 `IllegalArgumentException`（未注册/重复注销）统一吞掉；
 * - 注册失败记录 LogRing，不静默。
 */
class ReceiverHandle(
    private val tag: String,
    private val name: String,
    private val action: String,
    private val receiver: BroadcastReceiver,
) {
    private var registered = false

    fun register(context: Context) {
        if (registered) return
        registered = true
        try {
            val filter = IntentFilter(action)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            LogRing.d(tag, "receiver registered: $name")
        } catch (e: Exception) {
            registered = false
            LogRing.w(tag, "register receiver $name failed: ${e.message}")
        }
    }

    fun unregister(context: Context) {
        if (!registered) return
        registered = false
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // 已注销，忽略
        }
        LogRing.d(tag, "receiver unregistered: $name")
    }
}
