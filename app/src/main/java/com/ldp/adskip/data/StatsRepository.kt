package com.ldp.adskip.data

import android.content.Context

/**
 * 统计仓库：跳过计数（全局/按应用）与跳过日志的唯一入口。
 */
class StatsRepository(private val context: Context) {

    /** 一条跳过日志：时间戳、包名、应用名。 */
    data class LogEntry(val ts: Long, val pkg: String, val label: String)

    /** 记录一次跳过，返回累计总数。 */
    fun recordSkip(pkg: String, label: String): Int = Prefs.recordSkip(context, pkg, label)

    fun total(): Int = Prefs.getTotalSkips(context)

    fun lastApp(): String = Prefs.getLastApp(context)

    fun countFor(pkg: String): Int = Prefs.getPkgSkipCount(context, pkg)

    fun logs(): List<LogEntry> =
        Prefs.getLogs(context).map { LogEntry(it.first, it.second, it.third) }

    fun clearLogs() = Prefs.clearLogs(context)
}
