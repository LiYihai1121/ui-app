package com.ldp.adskip.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService

/**
 * 统计仓库：跳过计数（全局/按应用）与跳过日志的唯一入口。
 *
 * v2.2 增强：
 * - 合批落盘：计数先进内存，[Handler.postDelayed](5s) 延迟落盘，
 *   广播/退出时强制 flush，消除「每跳一次同步写 SP」。
 * - 读接口返回 SP 值 + 内存待落盘增量，保证实时一致。
 */
class StatsRepository(
    private val context: Context,
    private val ioExecutor: ExecutorService? = null
) {

    data class LogEntry(val ts: Long, val pkg: String, val label: String)

    private val flushHandler = Handler(Looper.getMainLooper())

    @Volatile private var pendingTotal = 0
    @Volatile private var pendingByPkg = mutableMapOf<String, Int>()
    @Volatile private var dirty = false

    private val flushRunnable = Runnable { flush() }

    /** 记录一次跳过，返回累计总数（含待落盘增量）。 */
    fun recordSkip(pkg: String, label: String): Int {
        // 日志量小可同步写
        Prefs.recordSkip(context, pkg, label)

        // 计数先进内存
        pendingTotal++
        pendingByPkg[pkg] = (pendingByPkg[pkg] ?: 0) + 1
        dirty = true

        // 5 秒后落盘
        flushHandler.removeCallbacks(flushRunnable)
        flushHandler.postDelayed(flushRunnable, 5000)

        return total()
    }

    /** 强制落盘（广播/退出时调用） */
    fun flush() {
        if (!dirty) return
        flushHandler.removeCallbacks(flushRunnable)
        val totalDelta = pendingTotal
        val pkgDeltas = pendingByPkg.toMap()
        dirty = false

        val action = Runnable {
            // 一次批量提交所有增量
            val editor = Prefs.sp(context).edit()
            val currentTotal = Prefs.getTotalSkips(context) + totalDelta
            editor.putInt("total_skips", currentTotal)
            for ((pkg, delta) in pkgDeltas) {
                val current = Prefs.getPkgSkipCount(context, pkg) + delta
                editor.putInt("pkg_count:$pkg", current)
            }
            editor.apply()
        }

        if (ioExecutor != null) ioExecutor.execute(action) else action.run()
    }

    fun total(): Int = Prefs.getTotalSkips(context) + pendingTotal

    fun lastApp(): String = Prefs.getLastApp(context)

    fun countFor(pkg: String): Int =
        Prefs.getPkgSkipCount(context, pkg) + (pendingByPkg[pkg] ?: 0)

    fun logs(): List<LogEntry> =
        Prefs.getLogs(context).map { LogEntry(it.first, it.second, it.third) }

    fun clearLogs() = Prefs.clearLogs(context)
}
