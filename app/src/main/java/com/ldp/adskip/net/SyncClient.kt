package com.ldp.adskip.net

import android.content.Context
import android.os.Handler
import android.os.Looper
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
 * - 原生 HttpURLConnection，零第三方依赖，全部后台线程执行
 * - 只负责「传输 + 解析」，规则落地委托给 [RulesRepository]
 * - 上报静默失败：服务端不在线是正常场景，不影响本地功能
 */
object SyncClient {

    private const val TIMEOUT_MS = 6000
    private val mainHandler = Handler(Looper.getMainLooper())

    // ---------- 配置（服务器地址 / 上次同步时间） ----------
    fun serverUrl(context: Context): String = Prefs.getServerUrl(context)
    fun saveServerUrl(context: Context, url: String) = Prefs.saveServerUrl(context, url)
    fun lastSyncAt(context: Context): Long = Prefs.getLastSyncAt(context)

    /**
     * 拉取云端规则并落地。
     * @param onResult 主线程回调：(成功?, 提示信息)
     */
    fun syncRules(
        context: Context,
        serverUrl: String,
        rulesRepo: RulesRepository,
        onResult: (Boolean, String) -> Unit
    ) {
        Thread {
            val result = try {
                val body = httpGet(serverUrl.trimEnd('/') + "/api/rules/latest")
                val json = JSONObject(body)

                val keywords = json.optJSONArray("keywords")?.toStringList()
                val viewIds = json.optJSONArray("viewIds")?.toStringList()
                val pkgRules = mutableMapOf<String, RulesRepository.PkgRule>()
                val packages = json.optJSONObject("packages")
                if (packages != null) {
                    val it = packages.keys()
                    while (it.hasNext()) {
                        val pkg = it.next()
                        val rule = packages.optJSONObject(pkg) ?: continue
                        pkgRules[pkg] = RulesRepository.PkgRule(
                            keywords = rule.optJSONArray("keywords")?.toStringList() ?: emptyList(),
                            viewIds = rule.optJSONArray("viewIds")?.toStringList() ?: emptyList(),
                            disabled = rule.optBoolean("disabled", false)
                        )
                    }
                }
                rulesRepo.applyCloudRules(keywords, viewIds, pkgRules)
                Prefs.setLastSyncAt(context, System.currentTimeMillis())

                val version = json.optInt("version", 0)
                Pair(true, "同步成功：规则 v$version，含 ${pkgRules.size} 个应用专属规则")
            } catch (e: Exception) {
                Pair(false, "同步失败：" + (e.message ?: "网络错误"))
            }
            mainHandler.post { onResult(result.first, result.second) }
        }.start()
    }

    /** 静默上报一次跳过。 */
    fun reportSkip(serverUrl: String, pkg: String, label: String) {
        if (serverUrl.isBlank()) return
        Thread {
            try {
                val payload = JSONObject()
                payload.put("pkg", pkg)
                payload.put("label", label)
                httpPost(serverUrl.trimEnd('/') + "/api/skip", payload.toString())
            } catch (e: Exception) {
                // 静默失败
            }
        }.start()
    }

    // ---------- HTTP 原语 ----------
    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
        }
        try {
            if (conn.responseCode !in 200..299) throw Exception("HTTP " + conn.responseCode)
            return conn.inputStream.bufferedReader().use { it.readText() }
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
