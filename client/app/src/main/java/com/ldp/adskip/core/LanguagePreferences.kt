package com.ldp.adskip.core

import android.content.Context
import android.content.SharedPreferences

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
 *
 * 存储安全：经 [SecurePreferences] 打开加密 SharedPreferences，与 `data/Prefs` 共用
 * `adskip_prefs_enc`（单一写入口）。历史明文文件 `adskip_prefs` 是 `Prefs` 的「明文
 * 迁移源」——某段时期本文件误把它当普通偏好文件读写，语言设置会在 `Prefs` 首次业务
 * 访问整体清空时丢失，且标签明文落盘。现明文仅作一次性迁移源（见 [migrateLegacyLanguage]）。
 */
object LanguagePreferences {

    /** 历史明文存储文件名（迁移源；除 language 键外归 `Prefs` 的统一迁移管）。 */
    private const val LEGACY_SP_NAME = "adskip_prefs"

    private const val KEY_LANGUAGE = "language"

    private var prefsInstance: SharedPreferences? = null

    /** 已保存的语言 tag；`null` 表示跟随系统。 */
    fun languageTag(context: Context): String? {
        val prefs = prefs(context)
        val tag = prefs.getString(KEY_LANGUAGE, null)
        if (tag != null) return tag
        return migrateLegacyLanguage(context, prefs)
    }

    /** 写入语言 tag；传 `null` 表示跟随系统（清除应用级覆盖）。 */
    fun setLanguageTag(context: Context, tag: String?) {
        val editor = prefs(context).edit()
        if (tag == null) editor.remove(KEY_LANGUAGE) else editor.putString(KEY_LANGUAGE, tag)
        editor.apply()
    }

    private fun prefs(context: Context): SharedPreferences {
        prefsInstance?.let { return it }
        synchronized(this) {
            prefsInstance?.let { return it }
            val opened = SecurePreferences.open(context)
            prefsInstance = opened
            return opened
        }
    }

    /**
     * 加密存储中尚无 language 时，从历史明文一次性搬入（只搬自己的键）。
     *
     * 只在 `data/Prefs` 尚未执行其整体明文迁移时启作用：后者若先跑，明文已被清空，
     * 这里拿不到值直接返回；`Prefs` 的迁移也会把 language 键带进同一加密文件，
     * 因此两条路径不冲突，最终都收敛到加密存储的同一键。
     */
    private fun migrateLegacyLanguage(context: Context, target: SharedPreferences): String? {
        val legacy = context.getSharedPreferences(LEGACY_SP_NAME, Context.MODE_PRIVATE)
        val tag = legacy.getString(KEY_LANGUAGE, null) ?: return null
        target.edit().putString(KEY_LANGUAGE, tag).apply()
        legacy.edit().remove(KEY_LANGUAGE).apply()
        return tag
    }
}
