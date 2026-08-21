package com.ldp.adskip.data

import android.content.Context
import com.ldp.adskip.engine.RuleSet

/**
 * 规则仓库：所有「规则」读写的唯一入口。
 *
 * - 合并全局规则与应用专属规则，产出可直接用于匹配的 [RuleSet]
 * - 应用级开关（禁用列表）
 * - 云端规则落地（[applyCloudRules] 由网络层解析后调用）
 */
class RulesRepository(private val context: Context) {

    /** 应用专属规则（云端下发）。 */
    data class PkgRule(
        val keywords: List<String>,
        val viewIds: List<String>,
        val disabled: Boolean
    )

    /** 合并某应用生效的完整规则集；被禁用时返回空规则。 */
    fun ruleSetFor(pkg: String): RuleSet {
        if (Prefs.isPackageDisabled(context, pkg)) {
            return RuleSet(emptyList(), emptyList(), disabled = true)
        }
        val keywords = (Prefs.getKeywords(context) + Prefs.getPkgKeywords(context, pkg))
            .filter { it.isNotBlank() }
        val viewIds = (Prefs.getViewIds(context) + Prefs.getPkgViewIds(context, pkg))
            .filter { it.length >= 3 }
        return RuleSet(keywords, viewIds)
    }

    // ---------- 全局关键词（主页编辑） ----------
    fun keywords(): MutableList<String> = Prefs.getKeywords(context)
    fun saveKeywords(list: List<String>) = Prefs.saveKeywords(context, list)

    // ---------- 应用开关 ----------
    fun isDisabled(pkg: String): Boolean = Prefs.isPackageDisabled(context, pkg)
    fun setDisabled(pkg: String, disabled: Boolean) = Prefs.setPackageDisabled(context, pkg, disabled)

    // ---------- 云端规则落地 ----------
    fun applyCloudRules(
        keywords: List<String>?,
        viewIds: List<String>?,
        pkgRules: Map<String, PkgRule>
    ) {
        keywords?.let { Prefs.saveKeywords(context, it) }
        viewIds?.let { Prefs.saveViewIds(context, it) }
        Prefs.clearAllPkgRules(context)
        val disabled = mutableListOf<String>()
        for ((pkg, rule) in pkgRules) {
            Prefs.savePkgKeywords(context, pkg, rule.keywords)
            Prefs.savePkgViewIds(context, pkg, rule.viewIds)
            if (rule.disabled) disabled.add(pkg)
        }
        Prefs.replaceDisabledPackages(context, disabled)
    }
}
