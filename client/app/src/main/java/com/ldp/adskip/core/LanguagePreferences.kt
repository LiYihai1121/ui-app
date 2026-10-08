package com.ldp.adskip.core

import android.content.Context

/**
 * 界面语言偏好（跨层共享的横切设施）。
 *
 * 为什么放在 `core` 而不是 `data/Prefs`：
 * 语言选择需要被**两个互不相通的层**读取——
 * - `ui/`（设置页读写选择）被禁止 import `data.`；
 * - `device/`（`LocaleApplier` 在 Activity `attachBaseContext` 时读选择）同样被禁止 import `data.`。
 *
 * 而 `core` 是两侧都允许依赖的横切层（`device → core`、`ui → core` 均合法），
 * 且它本身只依赖 Android 与 `core.*`，不违反「core 零业务包依赖」。
 * 把键与读写收在这里，`Prefs` 与 `SettingsRepository` 只做委托，避免出现第二份真相。
 *
 * 存储的是 **tag 字符串**（`zh-CN` / `en` / `null`=跟随系统），不是业务枚举：
 * 存储格式与业务模型解耦，改枚举名或换存储都不必迁移数据。
 */
object LanguagePreferences {

    private const val KEY_LANGUAGE = "language"

    /** 已保存的语言 tag；`null` 表示跟随系统。 */
    fun languageTag(context: Context): String? = prefs(context).getString(KEY_LANGUAGE, null)

    /** 写入语言 tag；传 `null` 表示跟随系统（清除应用级覆盖）。 */
    fun setLanguageTag(context: Context, tag: String?) {
        prefs(context).edit()
            .apply { if (tag == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, tag) }
            .apply()
    }

    /**
     * 走 [SecureStore] 加密存储。
     *
     * 历史原因本键曾与 `data/Prefs` 共用明文文件 `adskip_prefs`；现在两者统一
     * 落在加密存储，且旧键已由 SecureStore 的全量迁移带入——不会再出现
     * 「Prefs 迁移后删源文件，语言选择跟着丢」的数据丢失。
     */
    private fun prefs(context: Context) = SecureStore.prefs(context)
}
