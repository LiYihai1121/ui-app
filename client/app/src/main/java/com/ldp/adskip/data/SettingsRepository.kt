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
class SettingsRepository(private val context: Context, private val rulesRepo: RulesRepository) {

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

    // ---------- 应用元数据（供「我的」页展示） ----------

    /**
     * 应用版本展示值，形如 `3.1 (10)`（versionName + versionCode）。
     *
     * 为什么由 data 层查而不是让 UI 读 `BuildConfig`：约定插件
     * `adskip.android.application` 未开启 `buildConfig`，`BuildConfig` 并未生成；
     * 为一行展示文案打开它会给整个模块增加生成产物。对**本应用**查询
     * `PackageManager` 也不受 Android 11+ 包可见性限制。
     *
     * 仅当系统卸载/替换本应用时理论上会抛 `NameNotFoundException`，故兜底空串
     * 而非让「我的」页崩溃。
     */
    fun appVersionDisplay(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        formatVersion(info.versionName, code)
    }.getOrElse { "" }

    companion object {
        /**
         * 版本展示格式：`3.1 (10)`。
         *
         * 独立为纯函数是为了能在纯 JVM 单测中断言——本仓库真实踩过的坑：
         * 该行曾因脚本写入时 `$` 被转义成 `` "$`{info.versionName} ($code)" ``，
         * 模板不求值、编译照过，界面上却直接显示 `${info.versionName} (10)` 字面量。
         * 这类错误编译期无法发现，只能靠测试与真机走查兜底。
         */
        fun formatVersion(versionName: String?, versionCode: Long): String = "${versionName ?: "?"} ($versionCode)"
    }
}
