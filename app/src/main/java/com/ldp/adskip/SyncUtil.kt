package com.ldp.adskip

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 云端规则同步与跳过上报。
 * 使用原生 HttpURLConnection，零第三方依赖；全部在后台线程执行。
 */
object SyncUtil {

    private const val TIMEOUT_MS = 6000
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 从服务端拉取规则并写入本地。
     * @param onResult 主线程回调：(成功?, 提示信息)
     */
    fun syncRules(context: Context, serverUrl: String, onResult: (Boolean, String) -> Unit) {
        Thread {
            val result = try {
                val url = URL(serverUrl.trimEnd('/') + "/api/rules/latest")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                }
                if (conn.responseCode !in 200..299) throw Exception("HTTP " + conn.responseCode)
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = JSONObject(body)
                val keywords = json.optJSONArray("keywords")
                val viewIds = json.optJSONArray("viewIds")
                val packages = json.optJSONObject("packages")
                val version = json.optInt("version", 0)

                // 全局规则
                if (keywords != null) Prefs.saveKeywords(context, keywords.toList())
                if (viewIds != null) Prefs.saveViewIds(context, viewIds.toList())

                // 应用专属规则
                var pkgCount = 0
                if (packages != null) {
                    val disabled = mutableListOf<String>()
                    val it = packages.keys()
                    while (it.hasNext()) {
                        val pkg = it.next()
                        val rule = packages.optJSONObject(pkg) ?: continue
                        rule.optJSONArray("keywords")?.let { Prefs.savePkgKeywords(context, pkg, it.toList()) }
                        rule.optJSONArray("viewIds")?.let { Prefs.savePkgViewIds(context, pkg, it.toList()) }
                        if (rule.optBoolean("disabled", false)) disabled.add(pkg)
                        pkgCount++
                    }
                    Prefs.replaceDisabledPackages(context, disabled)
                }

                Prefs.setLastSyncAt(context, System.currentTimeMillis())
                Pair(true, "同步成功：规则 v$version，含 $pkgCount 个应用专属规则")
            } catch (e: Exception) {
                Pair(false, "同步失败：" + (e.message ?: "网络错误"))
            }
            mainHandler.post { onResult(result.first, result.second) }
        }.start()
    }

    /** 静默上报一次跳过（失败不影响任何本地功能）。 */
    fun reportSkip(context: Context, serverUrl: String, pkg: String, label: String) {
        if (serverUrl.isBlank()) return
        Thread {
            try {
                val url = URL(serverUrl.trimEnd('/') + "/api/skip")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    doOutput = true
                }
                val payload = JSONObject()
                payload.put("pkg", pkg)
                payload.put("label", label)
                BufferedWriter(OutputStreamWriter(conn.outputStream, "UTF-8")).use {
                    it.write(payload.toString())
                }
                conn.responseCode // 触发发送
                conn.disconnect()
            } catch (e: Exception) {
                // 静默失败：服务端不在线属正常场景
            }
        }.start()
    }

    private fun org.json.JSONArray.toList(): List<String> {
        val out = mutableListOf<String>()
        for (i in 0 until length()) {
            val s = optString(i).trim()
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }
}
