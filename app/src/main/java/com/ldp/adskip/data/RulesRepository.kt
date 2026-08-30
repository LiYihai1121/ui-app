package com.ldp.adskip.data

import android.content.Context
import android.util.LruCache
import com.ldp.adskip.engine.RuleSet

/**
 * 规则仓库：所有「规则」读写的唯一入口。
 *
 * v2.2 增强：
 * - [LruCache] 按 `(pkg → version)` 缓存合并结果，事件高频路径上只剩查表
 * - 仓库维护 `version` 计数，任何落地操作 `+1` 使缓存失效
 * - 云规则落地前校验 schemaVersion
 */
class RulesRepository(private val context: Context) {

    /** 应用专属规则（云端下发）。 */
    data class PkgRule(
        val keywords: List<String>,
        val viewIds: List<String>,
        val disabled: Boolean
    )

    private var version = 0
    private val cache = LruCache<String, RuleSet>(64)

    /** 合并某应用生效的完整规则集；被禁用时返回空规则。 */
    fun ruleSetFor(pkg: String): RuleSet {
        if (Prefs.isPackageDisabled(context, pkg)) {
            return RuleSet(emptyList(), emptyList(), disabled = true)
        }
        val cacheKey = "$pkg:$version"
        cache.get(cacheKey)?.let { return it }

        val keywords = (Prefs.getKeywords(context) + Prefs.getPkgKeywords(context, pkg))
            .filter { it.isNotBlank() }
        val viewIds = (Prefs.getViewIds(context) + Prefs.getPkgViewIds(context, pkg))
            .filter { it.length >= 3 }
        val ruleSet = RuleSet(keywords, viewIds)
        cache.put(cacheKey, ruleSet)
        return ruleSet
    }

    // ---------- 全局关键词（主页编辑） ----------
    fun keywords(): MutableList<String> = Prefs.getKeywords(context)
    fun saveKeywords(list: List<String>) {
        Prefs.saveKeywords(context, list)
        invalidate()
    }

    // ---------- 应用开关 ----------
    fun isDisabled(pkg: String): Boolean = Prefs.isPackageDisabled(context, pkg)
    fun setDisabled(pkg: String, disabled: Boolean) {
        Prefs.setPackageDisabled(context, pkg, disabled)
        invalidate()
    }

    // ---------- 云端规则落地（校验 schemaVersion） ----------
    fun applyCloudRules(
        keywords: List<String>?,
        viewIds: List<String>?,
        pkgRules: Map<String, PkgRule>,
        schemaVersion: Int = RuleSet.SCHEMA_VERSION
    ): Boolean {
        // 校验 schemaVersion：低于客户端支持的版本拒载
        if (schemaVersion < RuleSet.MIN_SCHEMA_VERSION) {
            return false
        }
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
        invalidate()
        return true
    }

    /** 使缓存失效 */
    private fun invalidate() {
        version++
        cache.evictAll()
    }
}
