package com.ldp.adskip.net

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ldp.adskip.core.AppExecutors
import com.ldp.adskip.core.LogRing
import com.ldp.adskip.data.Prefs
import com.ldp.adskip.data.RulesRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 网络层：云端规则同步与跳过上报。
 *
 * v2.2 增强：
 * - v1 协议：If-None-Match / 304、deviceId 限频维度、批量补报
 * - 经 AppExecutors.io 线程执行（调用方传入 executor）
 * - 上报静默失败：服务端不在线是正常场景，不影响本地功能
 *
 * 仍为零第三方依赖（HttpURLConnection + org.json）。
 */
object SyncClient {

    private const val TIMEOUT_MS = 6000
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executors by lazy { AppExecutors() }

    // ---------- 配置 ----------

    fun serverUrl(context: Context): String = Prefs.getServerUrl(context)
    fun saveServerUrl(context: Context, url: String) = Prefs.saveServerUrl(context, url)
    fun lastSyncAt(context: Context): Long = Prefs.getLastSyncAt(context)

    private data class RulesParseResult(
        val keywords: List<String>?,
        val viewIds: List<String>?,
        val selectors: List<String>?,
        val pkgRules: Map<String, RulesRepository.PkgRule>,
        val schemaVersion: Int,
        val hash: String,
        val version: Int,
    )

    /** v1/v0 规则响应统一解析；两端形状的差异在此收敛。 */
    private fun parseRulesResponse(body: String): RulesParseResult {
        val json = JSONObject(body)
        val schemaVersion = json.optInt("schemaVersion", 1)
        val hash = json.optString("hash", "")
        val version = json.optInt("version", 0)

        val rulesObj = json.optJSONObject("rules")
        val keywords: List<String>?
        val viewIds: List<String>?
        val selectors: List<String>?
        val pkgRules = mutableMapOf<String, RulesRepository.PkgRule>()

        if (rulesObj != null) {
            keywords = rulesObj.optJSONArray("globalKeywords")?.toStringList()
            viewIds = rulesObj.optJSONArray("globalViewIds")?.toStringList()
            selectors = rulesObj.optJSONArray("globalSelectors")?.toStringList()
            val apps = rulesObj.optJSONObject("apps")
            if (apps != null) {
                val keys = apps.keys()
                while (keys.hasNext()) {
                    val pkg = keys.next()
                    val rule = apps.optJSONObject(pkg) ?: continue
                    pkgRules[pkg] = RulesRepository.PkgRule(
                        keywords = rule.optJSONArray("keywords")?.toStringList() ?: emptyList(),
                        viewIds = rule.optJSONArray("viewIds")?.toStringList() ?: emptyList(),
                        selectors = rule.optJSONArray("selectors")?.toStringList() ?: emptyList(),
                        disabled = rule.optBoolean("disabled", false),
                    )
                }
            }
        } else {
            keywords = json.optJSONArray("keywords")?.toStringList()
            viewIds = json.optJSONArray("viewIds")?.toStringList()
            selectors = json.optJSONArray("selectors")?.toStringList()
            val packages = json.optJSONObject("packages")
            if (packages != null) {
                val keys = packages.keys()
                while (keys.hasNext()) {
                    val pkg = keys.next()
                    val rule = packages.optJSONObject(pkg) ?: continue
                    pkgRules[pkg] = RulesRepository.PkgRule(
                        keywords = rule.optJSONArray("keywords")?.toStringList() ?: emptyList(),
                        viewIds = rule.optJSONArray("viewIds")?.toStringList() ?: emptyList(),
                        selectors = rule.optJSONArray("selectors")?.toStringList() ?: emptyList(),
                        disabled = rule.optBoolean("disabled", false),
                    )
                }
            }
        }

        return RulesParseResult(keywords, viewIds, selectors, pkgRules, schemaVersion, hash, version)
    }

    /**
     * 拉取云端规则并落地（v1 协议：带 If-None-Match）。
     * @param onResult 主线程回调：(成功?, 提示信息)
     */
    fun syncRules(
        context: Context,
        serverUrl: String,
        rulesRepo: RulesRepository,
        onResult: (Boolean, String) -> Unit,
    ) {
        executors.io.execute {
            val result = try {
                val base = serverUrl.trimEnd('/')
                val knownHash = Prefs.getRulesHash(context)

                // v1 路由优先，回退 v0
                val (body, notModified) = httpGetWithETagBlocking("$base/api/v1/rules/latest", knownHash)
                if (notModified) {
                    Prefs.setLastSyncAt(context, System.currentTimeMillis())
                    Pair(true, "规则已是最新（304 Not Modified）")
                } else {
                    val parsed = parseRulesResponse(body)
                    val ok = rulesRepo.applyCloudRules(
                        parsed.keywords,
                        parsed.viewIds,
                        parsed.pkgRules,
                        parsed.schemaVersion,
                        parsed.selectors,
                    )
                    if (!ok) {
                        Pair(false, "规则协议版本过低，请升级客户端")
                    } else {
                        Prefs.setRulesHash(context, parsed.hash)
                        Prefs.setLastSyncAt(context, System.currentTimeMillis())
                        Pair(true, "同步成功：规则 v${parsed.version}，含 ${parsed.pkgRules.size} 个应用专属规则")
                    }
                }
            } catch (e: Exception) {
                Pair(false, "同步失败：" + (e.message ?: "网络错误"))
            }
            mainHandler.post { onResult(result.first, result.second) }
        }
    }

    /** 静默上报一次跳过（无 deviceId，仅保留以兼容旧调用方；优先使用带 deviceId 的重载）。 */
    @Deprecated("Use reportSkip(serverUrl, pkg, label, deviceId)")
    fun reportSkip(serverUrl: String, pkg: String, label: String) {
        if (serverUrl.isBlank()) return
        executors.io.execute {
            try {
                val base = serverUrl.trimEnd('/')
                val v0Payload = JSONObject()
                v0Payload.put("pkg", pkg)
                v0Payload.put("label", label)
                httpPost("$base/api/skip", v0Payload.toString())
            } catch (e: Exception) {
                // 静默失败
            }
        }
    }

    /** 静默上报一次跳过（带 deviceId，v1 批量格式）。 */
    fun reportSkip(serverUrl: String, pkg: String, label: String, deviceId: String) {
        if (serverUrl.isBlank()) return
        executors.io.execute {
            try {
                val base = serverUrl.trimEnd('/')
                val payload = JSONObject()
                val event = JSONObject()
                event.put("pkg", pkg)
                event.put("channel", "text")
                event.put("ts", System.currentTimeMillis())
                payload.put("deviceId", deviceId)
                payload.put("events", JSONArray().put(event))
                try {
                    httpPost("$base/api/v1/reports/batch", payload.toString())
                } catch (e: Exception) {
                    // 回退 v0
                    val v0Payload = JSONObject()
                    v0Payload.put("pkg", pkg)
                    v0Payload.put("label", label)
                    httpPost("$base/api/skip", v0Payload.toString())
                }
            } catch (e: Exception) {
                // 静默失败
            }
        }
    }

    /**
     * 静默同步规则（无 UI 回调，供 JobService 调用）。
     * 在调用方的 IO 线程中直接执行，不另起线程。
     */
    fun syncRulesSilently(context: Context, serverUrl: String, rulesRepo: RulesRepository): Boolean {
        return try {
            val base = serverUrl.trimEnd('/')
            val knownHash = Prefs.getRulesHash(context)
            val (body, notModified) = httpGetWithETagBlocking("$base/api/v1/rules/latest", knownHash)
            if (notModified) {
                Prefs.setLastSyncAt(context, System.currentTimeMillis())
                return true
            }
            val parsed = parseRulesResponse(body)
            val ok = rulesRepo.applyCloudRules(
                parsed.keywords,
                parsed.viewIds,
                parsed.pkgRules,
                parsed.schemaVersion,
                parsed.selectors,
            )
            if (ok) {
                Prefs.setRulesHash(context, parsed.hash)
                Prefs.setLastSyncAt(context, System.currentTimeMillis())
            }
            ok
        } catch (e: Exception) {
            com.ldp.adskip.core.LogRing.w("Sync", "syncRulesSilently failed: ${e.message}")
            false
        }
    }

    // ---------- HTTP 原语 ----------

    /** GET 带 If-None-Match，返回 (body, notModified) —— 同步阻塞版 */
    private fun httpGetWithETagBlocking(url: String, etag: String): Pair<String, Boolean> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            if (etag.isNotEmpty()) setRequestProperty("If-None-Match", etag)
        }
        try {
            if (conn.responseCode == 304) return Pair("", true)
            if (conn.responseCode !in 200..299) throw Exception("HTTP " + conn.responseCode)
            return Pair(conn.inputStream.bufferedReader().use { it.readText() }, false)
        } finally {
            conn.disconnect()
        }
    }

    private fun httpPost(url: String, body: String) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 3000
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
        }
        try {
            BufferedWriter(OutputStreamWriter(conn.outputStream, "UTF-8")).use { it.write(body) }
            conn.responseCode // 触发发送
        } finally {
            conn.disconnect()
        }
    }

    private fun JSONArray.toStringList(): List<String> {
        val out = mutableListOf<String>()
        for (i in 0 until length()) {
            val s = optString(i).trim()
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }
}
