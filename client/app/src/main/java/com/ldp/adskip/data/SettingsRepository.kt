package com.ldp.adskip.data

import android.content.Context
import com.ldp.adskip.core.LanguagePreferences

/**
 * 设置页数据门面（Repository 模式）。
 *
 * 收口「免打扰时段 / 界面语言 / 应用元数据」的读写，
 * 使 ui 层不直接依赖 [Prefs]（原始偏好）——
 * 边界契约见 ARCHITECTURE.md 第 2.1 节，由 ArchitectureBoundaryTest 守护。
 *
 * 纯本地架构：云端规则同步、后台同步调度、服务器地址等 API 已随 C/S 改造移除。
 */
class SettingsRepository(private val context: Context) {

    // ---------- 免打扰时段 ----------

    fun isDoNotDisturbEnabled(): Boolean = Prefs.isDoNotDisturbEnabled(context)

    fun setDoNotDisturbEnabled(enabled: Boolean) = Prefs.setDoNotDisturbEnabled(context, enabled)

    fun getDoNotDisturbStart(): Int = Prefs.getDoNotDisturbStart(context)

    fun getDoNotDisturbEnd(): Int = Prefs.getDoNotDisturbEnd(context)

    fun setDoNotDisturbTimes(startMinute: Int, endMinute: Int) =
        Prefs.setDoNotDisturbTimes(context, startMinute, endMinute)

    // ---------- 界面语言 ----------

    /**
     * 语言选择（`null` = 跟随系统）。
     *
     * 存储实体在 `core/LanguagePreferences`：`ui/` 与 `device/` 都被禁止 import `data/`，
     * 而两侧都要读它，故放在双方都允许依赖的 `core`。此处只做门面转发，不另存一份。
     */
    fun languageTag(): String? = LanguagePreferences.languageTag(context)

    fun setLanguageTag(tag: String?) = LanguagePreferences.setLanguageTag(context, tag)

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
