package com.qingqi.adskip.ui

import androidx.annotation.StringRes
import com.qingqi.adskip.R
import com.qingqi.adskip.device.LanguageMode

/**
 * 语言模式 → 显示文案资源的唯一映射。
 *
 * 此前 ProfileScreen 与 SettingsScreen 各持有一份完全相同的 when 表，
 * 新增语言要改两处、漏一处就会出现两页显示不一致。收敛到此处后，
 * 语言选项清单的事实源只有 [LanguageMode] 枚举 + 本映射。
 *
 * 放在 ui 层而不是 device/LanguageMode.kt：那里刻意不引用任何 Android
 * 类型以保持纯 JVM 可测（见其文件头注释），R.string 属于 UI 关注点。
 */
@StringRes
fun LanguageMode.labelRes(): Int = when (this) {
    LanguageMode.SYSTEM -> R.string.language_system
    LanguageMode.CHINESE -> R.string.language_zh
    LanguageMode.ENGLISH -> R.string.language_en
}
