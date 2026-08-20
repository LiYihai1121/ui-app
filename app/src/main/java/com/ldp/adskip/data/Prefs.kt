package com.ldp.adskip.data

import android.content.Context
import com.ldp.adskip.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * 关键词/规则/日志/统计的本地存储。
 * 仅用 SharedPreferences + org.json，零第三方依赖。
 */
object Prefs {

    private const val SP_NAME = "adskip_prefs"
    private const val KEY_KEYWORDS = "keywords"
    private const val KEY_VIEW_IDS = "view_ids"
    private const val KEY_DISABLED = "disabled_packages"
    private const val KEY_TOTAL = "total_skips"
    private const val KEY_LAST_APP = "last_app"
    private const val KEY_LOGS = "logs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_LAST_SYNC = "last_sync_at"

    private const val PREFIX_PKG_KEYWORDS = "pkg_kw:"
    private const val PREFIX_PKG_VIEW_IDS = "pkg_vid:"
    private const val PREFIX_PKG_COUNT = "pkg_count:"

    private const val LOG_CAP = 200
    const val DEFAULT_SERVER = "http://192.168.1.100:3210"

    fun sp(context: Context) = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    // ---------- 全局规则 ----------
    fun getKeywords(context: Context): MutableList<String> {
        val defaults = context.resources.getStringArray(R.array.default_keywords).toList()
        val saved = sp(context).getStringSet(KEY_KEYWORDS, null)
        return (saved ?: defaults.toSet()).toMutableList()
    }

    fun saveKeywords(context: Context, list: List<String>) {
        sp(context).edit().putStringSet(KEY_KEYWORDS, list.toSet()).apply()
    }

    fun getViewIds(context: Context): MutableList<String> {
        val defaults = context.resources.getStringArray(R.array.default_view_ids).toList()
        val saved = sp(context).getStringSet(KEY_VIEW_IDS, null)
        return (saved ?: defaults.toSet()).toMutableList()
    }

    fun saveViewIds(context: Context, list: List<String>) {
        sp(context).edit().putStringSet(KEY_VIEW_IDS, list.toSet()).apply()
    }

    // ---------- 按应用规则 ----------
    fun getPkgKeywords(context: Context, pkg: String): List<String> =
        sp(context).getStringSet(PREFIX_PKG_KEYWORDS + pkg, null)?.toList() ?: emptyList()

    fun savePkgKeywords(context: Context, pkg: String, list: List<String>) {
        sp(context).edit().putStringSet(PREFIX_PKG_KEYWORDS + pkg, list.toSet()).apply()
    }

    fun getPkgViewIds(context: Context, pkg: String): List<String> =
        sp(context).getStringSet(PREFIX_PKG_VIEW_IDS + pkg, null)?.toList() ?: emptyList()

    fun savePkgViewIds(context: Context, pkg: String, list: List<String>) {
        sp(context).edit().putStringSet(PREFIX_PKG_VIEW_IDS + pkg, list.toSet()).apply()
    }

    fun isPackageDisabled(context: Context, pkg: String): Boolean =
        sp(context).getStringSet(KEY_DISABLED, emptySet())?.contains(pkg) == true

    fun setPackageDisabled(context: Context, pkg: String, disabled: Boolean) {
        val set = sp(context).getStringSet(KEY_DISABLED, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (disabled) set.add(pkg) else set.remove(pkg)
        sp(context).edit().putStringSet(KEY_DISABLED, set).apply()
    }

    fun replaceDisabledPackages(context: Context, list: List<String>) {
        sp(context).edit().putStringSet(KEY_DISABLED, list.toSet()).apply()
    }

    // ---------- 统计 ----------
    fun getTotalSkips(context: Context): Int = sp(context).getInt(KEY_TOTAL, 0)

    fun getLastApp(context: Context): String = sp(context).getString(KEY_LAST_APP, "") ?: ""

    fun getPkgSkipCount(context: Context, pkg: String): Int =
        sp(context).getInt(PREFIX_PKG_COUNT + pkg, 0)

    fun recordSkip(context: Context, pkg: String, label: String): Int {
        val total = getTotalSkips(context) + 1
        sp(context).edit()
            .putInt(KEY_TOTAL, total)
            .putString(KEY_LAST_APP, label)
            .putInt(PREFIX_PKG_COUNT + pkg, getPkgSkipCount(context, pkg) + 1)
            .apply()
        addLog(context, pkg, label)
        return total
    }

    // ---------- 跳过日志 ----------
    fun getLogs(context: Context): List<Triple<Long, String, String>> {
        val raw = sp(context).getString(KEY_LOGS, "[]") ?: "[]"
        val out = mutableListOf<Triple<Long, String, String>>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Triple(o.optLong("ts"), o.optString("pkg"), o.optString("label")))
            }
        } catch (e: Exception) {
            // 数据损坏则清空
            sp(context).edit().putString(KEY_LOGS, "[]").apply()
        }
        return out
    }

    private fun addLog(context: Context, pkg: String, label: String) {
        val arr = try {
            JSONArray(sp(context).getString(KEY_LOGS, "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        val o = JSONObject()
        o.put("ts", System.currentTimeMillis())
        o.put("pkg", pkg)
        o.put("label", label)
        // 新记录放最前
        val next = JSONArray()
        next.put(o)
        for (i in 0 until minOf(arr.length(), LOG_CAP - 1)) next.put(arr.get(i))
        sp(context).edit().putString(KEY_LOGS, next.toString()).apply()
    }

    fun clearLogs(context: Context) {
        sp(context).edit().putString(KEY_LOGS, "[]").apply()
    }

    // ---------- 云同步 ----------
    fun getServerUrl(context: Context): String =
        sp(context).getString(KEY_SERVER_URL, DEFAULT_SERVER) ?: DEFAULT_SERVER

    fun saveServerUrl(context: Context, url: String) {
        sp(context).edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun getLastSyncAt(context: Context): Long = sp(context).getLong(KEY_LAST_SYNC, 0L)

    fun setLastSyncAt(context: Context, ts: Long) {
        sp(context).edit().putLong(KEY_LAST_SYNC, ts).apply()
    }
}
