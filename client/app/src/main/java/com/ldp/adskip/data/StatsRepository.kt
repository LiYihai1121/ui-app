package com.ldp.adskip.data

import android.content.Context

/**
 * 统计仓库：跳过计数（全局/按应用）与跳过日志的唯一入口。
 *
 * 写入完全委托 [Prefs.recordSkip]，本类**不持有任何待落盘增量**。
 *
 * 历史缺陷（v3.1.1 修复）：本类曾同时维护 `pendingTotal` / `pendingByPkg` 内存增量
 * 并在 `flush()` 时叠加回 SP，但 `Prefs.recordSkip` 早已同步把 `total_skips` 与
 * `pkg_count:*` 各 +1 落盘——两者是**同一批 key 的第二个写入方**，且 `flush()`
 * 从不将增量清零。于是每跳一次、每 5 秒一次的 `flush()` 都会把累计增量再次叠加：
 *
 *     跳过 1 次 → SP=1，pending=1
 *     flush   → SP = 1 + 1 = 2   ← 真实值应为 1
 *     跳过再 1 → SP=2，pending=2
 *     flush   → SP = 2 + 2 = 4   ← 真实值应为 2
 *
 * 计数因此按「叠加全部历史增量」发散，且 `total()` 的 `SP + pending` 读取口径
 * 又二次重复，用户看到的跳过数约为真实值的 2 倍起跳。
 *
 * 现在计数只有 [Prefs] 一个写入方，读接口直接读 SP，天然无偏差，
 * 也不存在「读—清—写」非原子导致的并发丢计数问题。
 *
 * 跳过的唯一调用点在无障碍服务主线程，[SharedPreferences] 自身负责跨进程串行化，
 * 故无需再加同步域。
 */
class StatsRepository(private val context: Context) {

    data class LogEntry(val ts: Long, val pkg: String, val label: String)

    /**
     * 记录一次跳过，返回累计总数。
     *
     * 计数与日志由 [Prefs.recordSkip] 同步落盘，读接口随即可见。
     */
    fun recordSkip(pkg: String, label: String): Int = Prefs.recordSkip(context, pkg, label)

    /**
     * 保留给生命周期收尾（[com.ldp.adskip.service.SkipAdService.onDestroy]）调用。
     *
     * 计数已同步落盘，此处无需补写；方法体为空是为保持调用方契约不变。
     */
    fun flush() {
        // no-op：写入已在 recordSkip 时同步完成
    }

    fun total(): Int = Prefs.getTotalSkips(context)

    fun lastApp(): String = Prefs.getLastApp(context)

    fun countFor(pkg: String): Int = Prefs.getPkgSkipCount(context, pkg)

    fun logs(): List<LogEntry> = Prefs.getLogs(context).map { LogEntry(it.first, it.second, it.third) }

    fun clearLogs() = Prefs.clearLogs(context)

    /** 清空后的原样写回，仅供「清空后撤销」使用（见 [Prefs.restoreLogs]） */
    fun restoreLogs(entries: List<LogEntry>) = Prefs.restoreLogs(
        context,
        entries.map { Triple(it.ts, it.pkg, it.label) },
    )
}
