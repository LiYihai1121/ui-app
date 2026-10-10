package com.qingqi.adskip.device

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.qingqi.adskip.core.LanguagePreferences
import com.qingqi.adskip.core.LogRing
import java.util.Locale

/**
 * 把 [LanguageMode] 落到运行时。
 *
 * 两条路径，因为 minSdk 是 26 而平台 API 从 33 才有：
 *
 * - **API 33+**：`LocaleManager.setApplicationLocales`。系统级能力，会同时改变
 *   应用内所有 Activity 与系统为应用显示的名称（系统设置里的「应用语言」），
 *   不需要重建进程，也不需要自己包 Context。
 * - **API 26–32**：没有平台 API，只能在 Activity 的 `attachBaseContext` 里
 *   包一层带 locale 的 Context（见 [wrapContext]）。这要求 Activity 重建，
 *   因此调用方在改语言后需要 `recreate()`。
 *
 * 只做一条路径是本功能最常见的半成品：要么低版本失效，要么高版本与系统设置不同步。
 */
object LocaleApplier {

    private const val TAG = "LocaleApplier"

    /**
     * 应用语言选择。
     *
     * @return 是否走了平台路径（`true` 时调用方**不必**重建 Activity；
     *         `false` 表示低版本已写入偏好，但需要 `recreate()` 才生效）
     */
    fun apply(context: Context, mode: LanguageMode): Boolean {
        val locales = toLocaleList(mode)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applyViaPlatform(context, locales)
            true
        } else {
            // 低版本没有平台 API：偏好已由调用方写入，重建 Activity 时由
            // wrapContext 读取并生效。这里不改全局 Locale——那会污染同进程的
            // 其它组件（如无障碍服务读取的资源），而应用级覆盖只该影响界面。
            LogRing.d(TAG, "API < 33，语言将在 Activity 重建后生效：${mode.tag ?: "system"}")
            false
        }
    }

    /**
     * 当前应用级语言覆盖。
     *
     * 供「跟随系统」判定与测试使用。API 33+ 读平台，低版本返回 null
     * （低版本没有应用级覆盖这个概念，实际语言由系统决定）。
     */
    fun currentOverride(context: Context): LocaleList? = readPlatformOverride(context)

    private fun readPlatformOverride(context: Context): LocaleList? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return context.getSystemService(LocaleManager::class.java)?.applicationLocales
    }

    /**
     * 低版本路径：按应用级覆盖包一层 Context。
     *
     * 由 Activity 在 `attachBaseContext` 中调用，返回的 Context 参与资源解析，
     * 于是 `values-en` 会被正确选中。
     *
     * @param mode 当前选择；[LanguageMode.SYSTEM] 时原样返回 [base]，不做任何包装——
     *        跟随系统意味着不覆盖，而不是覆盖成系统语言（后者会在系统语言变化后失效）。
     */
    fun wrapContext(base: Context, mode: LanguageMode): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = LanguageMode.languageTagOrNull(mode) ?: return base
        applyLocale(base, Locale.forLanguageTag(tag))?.let { return it }
        return base
    }

    /**
     * 语言模式 → 平台 LocaleList。
     *
     * **`SYSTEM` 必须映射为空列表**：传具体 locale 会把它钉死，
     * 用户之后再选「跟随系统」也不会复原到系统语言。
     * 空的 LocaleList 正是平台用来表示「清除应用级覆盖」的取值。
     */
    private fun toLocaleList(mode: LanguageMode): LocaleList {
        val tag = LanguageMode.languageTagOrNull(mode) ?: return LocaleList.getEmptyLocaleList()
        return LocaleList.forLanguageTags(tag)
    }

    /** 包一层带 [locale] 的 Context；失败时返回 `null` 交由调用方回退。 */
    private fun applyLocale(base: Context, locale: Locale): Context? {
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return try {
            base.createConfigurationContext(config)
        } catch (e: Exception) {
            // 包 Context 失败不该让应用起不来：退回未覆盖的 Context，
            // 界面用系统语言，功能仍可用。
            LogRing.w(TAG, "createConfigurationContext 失败，回退系统语言: ${e.message}")
            null
        }
    }

    /**
     * 低版本路径的便捷入口：**自己读取**已保存的语言选择并包一层 Context。
     *
     * 为什么由本文件读偏好，而不是让 Activity 读了再传进来：
     * `ui/` 与 `device/` 都被禁止 import `data/`（ArchitectureBoundaryTest 守护的依赖倒置），
     * 而语言存储放在 `core/LanguagePreferences`——两侧都允许依赖的横切层。
     * 语言这件事本就是「系统集成」，让 device 层同时负责「读选择」与「应用选择」，
     * Activity 只说一句「按已保存的语言起」。
     */
    fun wrapContextWithSavedLanguage(base: Context): Context =
        wrapContext(base, LanguageMode.fromTag(LanguagePreferences.languageTag(base)))

    private fun applyViaPlatform(context: Context, locales: LocaleList) {
        val manager = context.getSystemService(LocaleManager::class.java)
        if (manager == null) {
            // 系统服务缺失（极少数定制 ROM）：不崩，交由低版本路径在重建后生效。
            LogRing.w(TAG, "LocaleManager 不可用，语言将在 Activity 重建后生效")
            return
        }
        try {
            manager.applicationLocales = locales
        } catch (e: Exception) {
            LogRing.w(TAG, "setApplicationLocales 失败: ${e.message}")
        }
    }
}
