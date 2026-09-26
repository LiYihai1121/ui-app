package com.ldp.adskip.data

import android.content.Context
import com.ldp.adskip.net.SyncClient
import com.ldp.adskip.sync.SyncJobService

/**
 * 设置页数据门面（Repository 模式）。
 *
 * 收口「服务器地址 / 手动同步 / 自动同步调度 / 免打扰时段」的读写，
 * 使 ui 层不直接依赖 [SyncClient]（网络）、[SyncJobService]（后台调度）与 [Prefs]（原始偏好）——
 * 边界契约见 ARCHITECTURE.md 第 2.1 节，由 ArchitectureBoundaryTest 守护。
 *
 * @param rulesRepo 同步结果落地规则仓库（[SyncClient.syncRules] 需要）
 */
class SettingsRepository(
    private val context: Context,
    private val rulesRepo: RulesRepository
) {

    // ---------- 服务器地址与手动同步 ----------

    fun serverUrl(): String = SyncClient.serverUrl(context)

    fun saveServerUrl(url: String) = SyncClient.saveServerUrl(context, url)

    fun lastSyncAt(): Long = SyncClient.lastSyncAt(context)

    /** 立即同步云端规则；[onResult] 主线程回调：(成功?, 提示信息) */
    fun syncNow(url: String, onResult: (Boolean, String) -> Unit) =
        SyncClient.syncRules(context, url, rulesRepo, onResult)

    // ---------- 自动同步 ----------

    fun isAutoSyncEnabled(): Boolean = Prefs.isAutoSyncEnabled(context)

    /** 写入偏好并注册/取消周期 Job */
    fun setAutoSyncEnabled(enabled: Boolean) = SyncJobService.setEnabled(context, enabled)

    // ---------- 免打扰时段 ----------

    fun isDoNotDisturbEnabled(): Boolean = Prefs.isDoNotDisturbEnabled(context)

    fun setDoNotDisturbEnabled(enabled: Boolean) = Prefs.setDoNotDisturbEnabled(context, enabled)

    fun getDoNotDisturbStart(): Int = Prefs.getDoNotDisturbStart(context)

    fun getDoNotDisturbEnd(): Int = Prefs.getDoNotDisturbEnd(context)

    fun setDoNotDisturbTimes(startMinute: Int, endMinute: Int) =
        Prefs.setDoNotDisturbTimes(context, startMinute, endMinute)
}