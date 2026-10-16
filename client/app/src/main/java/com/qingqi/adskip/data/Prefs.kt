package com.qingqi.adskip.data

import android.content.Context
import android.content.SharedPreferences
import com.qingqi.adskip.R
import com.qingqi.adskip.core.SecureStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * 关键词/规则/日志/统计的本地存储。
 * 存储经 [SecureStore]（EncryptedSharedPreferences）加密；
 * 历史明文数据在首次访问时一次性迁移到加密存储（迁移逻辑在 SecureStore）。
 */
object Prefs {

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
    private const val KEY_SEEN_ONBOARDING = "seen_onboarding"

    private const val PREFIX_PKG_KEYWORDS = "pkg_kw:"
    private const val PREFIX_PKG_VIEW_IDS = "pkg_vid:"
    private const val PREFIX_PKG_SELECTORS = "pkg_sel:"
    private const val PREFIX_PKG_COUNT = "pkg_count:"

    private const val LOG_CAP = 200

    /**
     * 默认服务器地址：空串。
     *
     * 不预填任何地址：预填 LAN 地址会在用户未配置时「看起来已配好」却每次同步
     * 必然失败（HTTPS 门禁要求证书指纹，局域网自签证书无法满足）。留空让设置
     * 页强制用户显式输入自己的有效 HTTPS 地址（[ServerEndpoint.isValid] 校验）。
     */
    const val DEFAULT_SERVER = ""

    /** 加密偏好存储；创建与明文历史迁移统一收口在 [SecureStore]（core 层唯一入口）。 */
    fun sp(context: Context): SharedPreferences = SecureStore.prefs(context)

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

    fun getPkgViewIds(context: Context, pkg: String): List<String> =
        sp(context).getStringSet(PREFIX_PKG_VIEW_IDS + pkg, null)?.toList() ?: emptyList()

    fun getPkgSelectors(context: Context, pkg: String): List<String> =
        sp(context).getStringSet(PREFIX_PKG_SELECTORS + pkg, null)?.toList() ?: emptyList()

    fun isPackageDisabled(context: Context, pkg: String): Boolean =
        sp(context).getStringSet(KEY_DISABLED, emptySet())?.contains(pkg) == true

    fun setPackageDisabled(context: Context, pkg: String, disabled: Boolean) {
        val set = sp(context).getStringSet(KEY_DISABLED, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (disabled) set.add(pkg) else set.remove(pkg)
        sp(context).edit().putStringSet(KEY_DISABLED, set).apply()
    }

    /**
     * 云端规则整体落地——**唯一的多键规则写入路径**（经 [RulesStore.replaceAllRules] 调用）。
     *
     * 单次 edit 原子提交全部键：此前 [RulesRepository.applyCloudRules] 拆成
     * 关键词/viewIds/selectors/清旧包规则/逐包保存/禁用列表六次独立提交，进程在
     * 任两次之间死亡就会留下「半新半旧」的混合规则（新关键词配旧禁用列表），
     * 且无法自愈（下一次同步以 hash 相同为由跳过）。一次 edit 内完成所有键的
     * 增删改，SharedPreferences 保证整批原子落盘：要么全落地，要么保持旧版。
     */
    fun replaceAllRules(
        context: Context,
        keywords: List<String>,
        viewIds: List<String>,
        selectors: List<String>,
        pkgRules: Map<String, CloudPkgRule>,
        disabled: List<String>,
    ) {
        val prefs = sp(context)
        val editor = prefs.edit()
        editor.putString(KEY_KEYWORDS_JSON, stringListToJson(keywords.filter { it.isNotBlank() }.distinct()))
        editor.putString(KEY_VIEW_IDS_JSON, stringListToJson(viewIds.filter { it.isNotBlank() }.distinct()))
        editor.putString(KEY_GLOBAL_SELECTORS_JSON, stringListToJson(selectors.filter { it.isNotBlank() }.distinct()))
        // 旧云端包规则键先在同一次 edit 内全部摘除，再写入新集合，杜绝「旧包残留」
        for (key in prefs.all.keys) {
            if (key.startsWith(PREFIX_PKG_KEYWORDS) || key.startsWith(PREFIX_PKG_VIEW_IDS) ||
                key.startsWith(PREFIX_PKG_SELECTORS)
            ) {
                editor.remove(key)
            }
        }
        for ((pkg, rule) in pkgRules) {
            editor.putStringSet(PREFIX_PKG_KEYWORDS + pkg, rule.keywords.toSet())
            editor.putStringSet(PREFIX_PKG_VIEW_IDS + pkg, rule.viewIds.toSet())
            editor.putStringSet(PREFIX_PKG_SELECTORS + pkg, rule.selectors.toSet())
        }
        editor.putStringSet(KEY_DISABLED, disabled.toSet())
        editor.apply()
    }

    // ---------- 统计 ----------
    fun getTotalSkips(context: Context): Int = sp(context).getInt(KEY_TOTAL, 0)

    fun getLastApp(context: Context): String = sp(context).getString(KEY_LAST_APP, "") ?: ""

    fun getPkgSkipCount(context: Context, pkg: String): Int = sp(context).getInt(PREFIX_PKG_COUNT + pkg, 0)

    /**
     * 追加一条跳过日志，并更新「最近跳过应用」。
     *
     * **刻意不写计数**：计数的唯一累加者是 [StatsRepository]，唯一落盘路径是
     * [StatsRepository.flush] → [addSkipCounts]。此前这里也写了一次 `total_skips`
     * 与 `pkg_count:*`，与 flush 的待落盘增量重复累加，真机表现为「单次跳过
     * 首页显示 2、5 秒后 3」（2026-10-11 实测）。同一事实只能有一份真值源。
     */
    fun appendSkipLog(context: Context, pkg: String, label: String) {
        sp(context).edit().putString(KEY_LAST_APP, label).apply()
        addLog(context, pkg, label)
    }

    /**
     * 批量累加跳过计数——**计数的唯一写入路径**（由 [StatsRepository.flush] 调用）。
     *
     * 一次性提交同一批增量，避免每个包各写一次 SP；读改写在同一处完成，
     * 保证「已落盘值 + 增量」的语义与 [StatsRepository.total] 的读法一致。
     */
    fun addSkipCounts(context: Context, totalDelta: Int, pkgDeltas: Map<String, Int>) {
        if (totalDelta == 0 && pkgDeltas.isEmpty()) return
        val editor = sp(context).edit()
        editor.putInt(KEY_TOTAL, getTotalSkips(context) + totalDelta)
        for ((pkg, delta) in pkgDeltas) {
            editor.putInt(PREFIX_PKG_COUNT + pkg, getPkgSkipCount(context, pkg) + delta)
        }
        editor.apply()
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
    fun getRulesSigningKey(context: Context): String = sp(context).getString(KEY_RULES_SIGNING_KEY, "") ?: ""

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

    // ---------- 引导页 ----------

    /**
     * 是否已看过首次启动引导页。
     *
     * 默认 false（新装用户看引导）；看过一次后置 true，此后不再自动展示。
     * 为什么落盘而不是放内存：引导页的价值在「第一次」，进程重启后若又弹一次，
     * 对老用户就是纯骚扰——而它恰恰是每次冷启动都会触发的场景。
     */
    fun hasSeenOnboarding(context: Context): Boolean = sp(context).getBoolean(KEY_SEEN_ONBOARDING, false)

    fun setSeenOnboarding(context: Context, seen: Boolean) {
        sp(context).edit().putBoolean(KEY_SEEN_ONBOARDING, seen).apply()
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
