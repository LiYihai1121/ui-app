package com.qingqi.adskip.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.qingqi.adskip.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * 关键词/规则/日志/统计的本地存储。
 * 存储经 AndroidX security-crypto 的 EncryptedSharedPreferences 加密；
 * 历史明文数据在首次访问时一次性迁移到加密存储。
 */
object Prefs {

    /** 旧明文存储文件名（迁移源）。 */
    private const val LEGACY_SP_NAME = "adskip_prefs"

    /** 新加密存储文件名。 */
    private const val SP_NAME = "adskip_prefs_enc"

    private var spInstance: SharedPreferences? = null

    /** 进程内迁移仅执行一次；迁移幂等（源为空即跳过）。 */
    private var migrated = false
    private const val KEY_KEYWORDS = "keywords"
    private const val KEY_KEYWORDS_JSON = "keywords_json"
    private const val KEY_VIEW_IDS = "view_ids"
    private const val KEY_VIEW_IDS_JSON = "view_ids_json"
    private const val KEY_GLOBAL_SELECTORS = "global_selectors"
    private const val KEY_GLOBAL_SELECTORS_JSON = "global_selectors_json"
    private const val KEY_DISABLED = "disabled_packages"
    private const val KEY_TOTAL = "total_skips"
    private const val KEY_LAST_APP = "last_app"
    private const val KEY_LOGS = "logs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_LAST_SYNC = "last_sync_at"
    private const val KEY_AUTO_SYNC = "auto_sync"
    private const val KEY_DND_ENABLED = "dnd_enabled"
    private const val KEY_DND_START = "dnd_start_minute"
    private const val KEY_DND_END = "dnd_end_minute"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_RULES_HASH = "rules_hash"
    private const val KEY_RULES_SIGNING_KEY = "rules_signing_key"
    private const val KEY_CERT_PINS = "cert_pins"

    private const val PREFIX_PKG_KEYWORDS = "pkg_kw:"
    private const val PREFIX_PKG_VIEW_IDS = "pkg_vid:"
    private const val PREFIX_PKG_SELECTORS = "pkg_sel:"
    private const val PREFIX_PKG_COUNT = "pkg_count:"

    private const val LOG_CAP = 200
    const val DEFAULT_SERVER = "http://192.168.1.100:3210"

    fun sp(context: Context): SharedPreferences {
        spInstance?.let { return it }
        synchronized(this) {
            spInstance?.let { return it }
            val encrypted = EncryptedSharedPreferences.create(
                context,
                SP_NAME,
                MasterKey.Builder(context).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            spInstance = encrypted
            migrateLegacy(context, encrypted)
            return encrypted
        }
    }

    private fun migrateLegacy(context: Context, target: SharedPreferences) {
        val legacy = context.getSharedPreferences(LEGACY_SP_NAME, Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) return
        val editor = target.edit()
        for ((key, value) in legacy.all) {
            when (value) {
                is String -> editor.putString(key, value)

                is Int -> editor.putInt(key, value)

                is Long -> editor.putLong(key, value)

                is Boolean -> editor.putBoolean(key, value)

                else -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
            }
        }
        editor.apply()
        runCatching { context.deleteSharedPreferences(LEGACY_SP_NAME) }
    }

    // ---------- 全局规则 ----------
    fun getKeywords(context: Context): MutableList<String> {
        val defaults = context.resources.getStringArray(R.array.default_keywords).toList()
        val prefs = sp(context)
        if (prefs.contains(KEY_KEYWORDS_JSON)) {
            return jsonToStringList(prefs.getString(KEY_KEYWORDS_JSON, null)).toMutableList()
        }
        val saved = prefs.getStringSet(KEY_KEYWORDS, null)?.toList() ?: defaults
        val ordered = saved.filter { it.isNotBlank() }.distinct()
        saveKeywords(context, ordered)
        return ordered.toMutableList()
    }

    fun saveKeywords(context: Context, list: List<String>) {
        val ordered = list.filter { it.isNotBlank() }.distinct()
        sp(context).edit().putString(KEY_KEYWORDS_JSON, stringListToJson(ordered)).apply()
    }

    fun getViewIds(context: Context): MutableList<String> {
        val defaults = context.resources.getStringArray(R.array.default_view_ids).toList()
        val prefs = sp(context)
        if (prefs.contains(KEY_VIEW_IDS_JSON)) {
            return jsonToStringList(prefs.getString(KEY_VIEW_IDS_JSON, null)).toMutableList()
        }
        val saved = prefs.getStringSet(KEY_VIEW_IDS, null)?.toList() ?: defaults
        val ordered = saved.filter { it.isNotBlank() }.distinct()
        saveViewIds(context, ordered)
        return ordered.toMutableList()
    }

    fun saveViewIds(context: Context, list: List<String>) {
        val ordered = list.filter { it.isNotBlank() }.distinct()
        sp(context).edit().putString(KEY_VIEW_IDS_JSON, stringListToJson(ordered)).apply()
    }

    fun getGlobalSelectors(context: Context): MutableList<String> {
        val prefs = sp(context)
        if (prefs.contains(KEY_GLOBAL_SELECTORS_JSON)) {
            return jsonToStringList(prefs.getString(KEY_GLOBAL_SELECTORS_JSON, null)).toMutableList()
        }
        val saved = prefs.getStringSet(KEY_GLOBAL_SELECTORS, null)?.toList() ?: emptyList()
        val ordered = saved.filter { it.isNotBlank() }.distinct()
        saveGlobalSelectors(context, ordered)
        return ordered.toMutableList()
    }

    fun saveGlobalSelectors(context: Context, list: List<String>) {
        val ordered = list.filter { it.isNotBlank() }.distinct()
        sp(context).edit().putString(KEY_GLOBAL_SELECTORS_JSON, stringListToJson(ordered)).apply()
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

    fun getPkgSelectors(context: Context, pkg: String): List<String> =
        sp(context).getStringSet(PREFIX_PKG_SELECTORS + pkg, null)?.toList() ?: emptyList()

    fun savePkgSelectors(context: Context, pkg: String, list: List<String>) {
        sp(context).edit().putStringSet(PREFIX_PKG_SELECTORS + pkg, list.toSet()).apply()
    }

    /** 清空所有云端下发的应用专属规则，用于同步时先做完整覆盖。 */
    fun clearAllPkgRules(context: Context) {
        val prefs = sp(context)
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith(PREFIX_PKG_KEYWORDS) || key.startsWith(PREFIX_PKG_VIEW_IDS) ||
                key.startsWith(PREFIX_PKG_SELECTORS)
            ) {
                editor.remove(key)
            }
        }
        editor.apply()
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

    fun getPkgSkipCount(context: Context, pkg: String): Int = sp(context).getInt(PREFIX_PKG_COUNT + pkg, 0)

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

    /**
     * 整体写回日志，仅用于「清空后撤销」把原数据原样放回。
     *
     * 与 [addLog] 的区别：addLog 是追加并按 [LOG_CAP] 裁剪、保持时间序；
     * 本方法**原样覆盖**，因此调用方必须传入清空前读到的完整快照，
     * 且快照自身应已由 [getLogs] 保证长度 ≤ LOG_CAP。
     * 之所以需要它：清空日志是不可逆操作，没有恢复途径就等于「删了就没了」；
     * 提供写回能力后，界面才能给出真正的「撤销」而不仅是二次确认。
     */
    fun restoreLogs(context: Context, entries: List<Triple<Long, String, String>>) {
        if (entries.size > LOG_CAP) return
        val next = JSONArray()
        for ((ts, pkg, label) in entries) {
            val o = JSONObject()
            o.put("ts", ts)
            o.put("pkg", pkg)
            o.put("label", label)
            next.put(o)
        }
        sp(context).edit().putString(KEY_LOGS, next.toString()).apply()
    }

    // ---------- 云同步 ----------
    fun getServerUrl(context: Context): String = sp(context).getString(KEY_SERVER_URL, DEFAULT_SERVER) ?: DEFAULT_SERVER

    fun saveServerUrl(context: Context, url: String) {
        sp(context).edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun getLastSyncAt(context: Context): Long = sp(context).getLong(KEY_LAST_SYNC, 0L)

    fun setLastSyncAt(context: Context, ts: Long) {
        sp(context).edit().putLong(KEY_LAST_SYNC, ts).apply()
    }

    // ---------- 体验设置 ----------
    fun isAutoSyncEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_AUTO_SYNC, false)

    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
    }

    fun isDoNotDisturbEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_DND_ENABLED, false)

    fun setDoNotDisturbEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_DND_ENABLED, enabled).apply()
    }

    fun getDoNotDisturbStart(context: Context): Int = sp(context).getInt(KEY_DND_START, 23 * 60)
    fun getDoNotDisturbEnd(context: Context): Int = sp(context).getInt(KEY_DND_END, 7 * 60)

    fun setDoNotDisturbTimes(context: Context, startMinute: Int, endMinute: Int) {
        sp(context).edit()
            .putInt(KEY_DND_START, startMinute)
            .putInt(KEY_DND_END, endMinute)
            .apply()
    }

    // ---------- 设备标识（上报限频维度） ----------
    fun getDeviceId(context: Context): String {
        val prefs = sp(context)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    // ---------- 规则哈希（If-None-Match 同步） ----------
    fun getRulesHash(context: Context): String = sp(context).getString(KEY_RULES_HASH, "") ?: ""
    fun setRulesHash(context: Context, hash: String) {
        sp(context).edit().putString(KEY_RULES_HASH, hash).apply()
    }

    // ---------- 规则签名密钥（HMAC-SHA256，与服务端 RULES_SIGNING_KEY 配对） ----------
    /**
     * 云端规则完整性密钥。
     *
     * 服务端对 rules/latest 响应体签发 HMAC-SHA256，客户端用同一密钥验签；
     * 未配置密钥时 [com.qingqi.adskip.net.SyncClient] 一律拒绝载入云端规则
     * （失败关闭）——读取端点无鉴权，签名是防规则被中间人改写的唯一手段。
     */
    fun getRulesSigningKey(context: Context): String =
        sp(context).getString(KEY_RULES_SIGNING_KEY, "") ?: ""

    fun saveRulesSigningKey(context: Context, key: String) {
        sp(context).edit().putString(KEY_RULES_SIGNING_KEY, key.trim()).apply()
    }

    // ---------- 证书指纹（OkHttp CertificatePinner，每行一个 sha256/...） ----------
    /** 取证书公钥指纹列表；为空表示未固定证书。 */
    fun getCertPins(context: Context): List<String> {
        val raw = sp(context).getString(KEY_CERT_PINS, "") ?: ""
        return raw.split('\n', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun saveCertPins(context: Context, pins: List<String>) {
        val cleaned = pins.map { it.trim() }.filter { it.isNotEmpty() }
        sp(context).edit().putString(KEY_CERT_PINS, cleaned.joinToString("\n")).apply()
    }

    // ---------- 有序字符串列表序列化 ----------
    private fun stringListToJson(list: List<String>): String {
        val arr = JSONArray()
        for (item in list) arr.put(item)
        return arr.toString()
    }

    private fun jsonToStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { index ->
                val item = arr.optString(index).trim()
                item.takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
