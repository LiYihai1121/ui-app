package com.qingqi.adskip.net

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.qingqi.adskip.core.AppExecutors
import com.qingqi.adskip.core.LogRing
import com.qingqi.adskip.data.Prefs
import com.qingqi.adskip.data.RulesRepository
import okhttp3.CertificatePinner
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 网络层：云端规则同步与跳过上报。
 *
 * 安全约束（A 链修复）：
 * - 规则完整性：服务端对 rules/latest 响应体签发 HMAC-SHA256，本类用
 *   [Prefs.getRulesSigningKey] 配的密钥验签后才允许落地；未配置密钥 = 拒绝
 *   载入（失败关闭），未通过验签的载荷一律丢弃。
 * - 证书固定：https 连接必须配置证书指纹（[Prefs.getCertPins]）才放行；
 *   未配置 = 拒绝连接，杜绝「声明了 pinner 却没有任何 pin」的空操作。
 * - 错误信息不回显 URL（可能携带 userinfo 凭据），日志只留主机名。
 */
object SyncClient {

    private const val TIMEOUT_MS = 6000
    private const val SIGNATURE_HEADER = "X-Rules-Signature"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executors by lazy { AppExecutors() }

    // ---------- 配置 ----------

    fun serverUrl(context: Context): String = Prefs.getServerUrl(context)
    fun saveServerUrl(context: Context, url: String) = Prefs.saveServerUrl(context, url)
    fun lastSyncAt(context: Context): Long = Prefs.getLastSyncAt(context)

    /** OkHttp 客户端按（host + pins）缓存：CertificatePinner.pin 是 host 维度，单例做不到按配置切换 */
    private val pinnedClients = ConcurrentHashMap<String, OkHttpClient>()

    private fun baseBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .writeTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)

    /** 无 pins 的通用客户端：仅上报等低敏请求使用 */
    private val plainClient: OkHttpClient by lazy { baseBuilder().build() }

    private fun clientForPins(host: String, pins: List<String>): OkHttpClient {
        val cacheKey = "$host|${pins.joinToString(",")}"
        return pinnedClients.getOrPut(cacheKey) {
            val pinner = CertificatePinner.Builder().apply {
                for (pin in pins) add(host, pin)
            }.build()
            baseBuilder().certificatePinner(pinner).build()
        }
    }

    // 测试用客户端（禁用证书锁定，允许 MockWebServer 自签名证书）
    internal var testClient: OkHttpClient? = null

    /** 上报等无 pins 场景的客户端；测试注入优先 */
    private fun client(): OkHttpClient = testClient ?: plainClient

    // ---------- 规则解析 ----------

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

    // ---------- 同步/上报 ----------

    /**
     * 拉取云端规则并落地（v1 协议：带 If-None-Match）。
     *
     * 失败关闭链：未配签名密钥 → 拒载；https 未配证书指纹 → 拒连；
     * 验签不通过 → 拒载。三者任一不满足都不触碰本地规则。
     *
     * @param onResult 主线程回调：(成功?, 提示信息)
     */
    fun syncRules(
        context: Context,
        serverUrl: String,
        rulesRepo: RulesRepository,
        onResult: (Boolean, String) -> Unit,
    ) {
        executors.io.execute {
            val result = runSync(context, serverUrl.trimEnd('/'), rulesRepo)
            mainHandler.post { onResult(result.first, result.second) }
        }
    }

    /** 单次同步的阻塞实现：验签/证书门禁 + 落地；失败一律不动本地规则 */
    private fun runSync(context: Context, base: String, rulesRepo: RulesRepository): Pair<Boolean, String> {
        val guard = securityGuard(context, base)
        if (guard != null) return Pair(false, guard)
        return try {
            val knownHash = Prefs.getRulesHash(context)
            val pins = Prefs.getCertPins(context)
            // v1 路由优先，回退 v0
            val (body, signature, notModified) =
                httpGetWithETagBlocking("$base/api/v1/rules/latest", knownHash, base, pins)
            if (notModified) {
                Prefs.setLastSyncAt(context, System.currentTimeMillis())
                Pair(true, "规则已是最新（304 Not Modified）")
            } else {
                val signingKey = Prefs.getRulesSigningKey(context)
                if (!verifyRulesSignature(signingKey, body, signature)) {
                    // 验签失败：载荷可能被中间人改写，直接丢弃
                    Pair(false, "规则签名校验失败，已拒绝载入（本地密钥与服务端不一致，或连接被劫持）")
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
            }
        } catch (e: Exception) {
            // 不回显 URL（可能含 userinfo 凭据），日志只留主机名
            LogRing.w("Sync", "sync failed for ${hostOf(base)}: ${e.javaClass.simpleName}")
            Pair(false, "同步失败：无法连接到服务器")
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
            val guard = securityGuard(context, base)
            if (guard != null) {
                LogRing.w("Sync", "silent sync refused: $guard")
                return false
            }
            val knownHash = Prefs.getRulesHash(context)
            val pins = Prefs.getCertPins(context)
            val (body, signature, notModified) =
                httpGetWithETagBlocking("$base/api/v1/rules/latest", knownHash, base, pins)
            if (notModified) {
                Prefs.setLastSyncAt(context, System.currentTimeMillis())
                return true
            }
            val signingKey = Prefs.getRulesSigningKey(context)
            if (!verifyRulesSignature(signingKey, body, signature)) {
                LogRing.w("Sync", "silent sync refused: signature mismatch")
                return false
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
            // 不回显 URL，日志只留主机名
            LogRing.w("Sync", "syncRulesSilently failed for ${hostOf(serverUrl)}: ${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * 同步前的失败关闭门禁：未配签名密钥一律拒载；https 未配证书指纹一律拒连。
     * @return 拒绝原因；通过时返回 null。
     */
    private fun securityGuard(context: Context, base: String): String? {
        if (Prefs.getRulesSigningKey(context).isBlank()) {
            return "未配置规则签名密钥，已拒绝云端规则（防篡改）。请在本页底部配置"
        }
        if (base.startsWith("https://", ignoreCase = true)) {
            if (Prefs.getCertPins(context).isEmpty()) {
                return "未配置证书指纹，已拒绝 HTTPS 连接。请在本页底部填入 sha256/... 指纹"
            }
        }
        return null
    }

    /** 从 URL 中去掉 scheme 与 userinfo，只留主机名（日志/提示不泄露完整地址） */
    internal fun hostOf(url: String): String = url.toHttpUrlOrNull()?.host ?: "invalid-url"

    // ---------- HTTP 原语 ----------

    private data class GetResult(val body: String, val signature: String?, val notModified: Boolean)

    /** GET 带 If-None-Match，返回 (body, 签名头, notModified) —— 同步阻塞版 */
    private fun httpGetWithETagBlocking(url: String, etag: String, baseUrl: String, pins: List<String>): GetResult {
        val host = hostOf(baseUrl)
        val httpClient = testClient ?: clientForPins(host, pins)
        val request = Request.Builder()
            .url(url)
            .header("If-None-Match", etag)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 304) return GetResult("", null, true)
            if (response.code !in 200..299) throw Exception("HTTP " + response.code)
            return GetResult(
                response.body!!.string(),
                response.header(SIGNATURE_HEADER),
                false,
            )
        }
    }

    // ---------- 规则签名（HMAC-SHA256，与服务端 sign.ts 同算法同口径） ----------

    /** 对载荷原文计算 HMAC-SHA256 十六进制；服务端同样按最终字节串签发 */
    internal fun hmacSha256Hex(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /**
     * 验签：密钥为空、签名为空、长度不一致均判失败（失败关闭）。
     * 比较用常量时间算法，避免逐字节提前命中泄露前缀信息。
     */
    internal fun verifyRulesSignature(key: String, body: String, signature: String?): Boolean {
        if (key.isBlank() || signature.isNullOrBlank()) return false
        val presented = signature.trim().removePrefix("sha256=").lowercase()
        val expected = hmacSha256Hex(key, body)
        return constantTimeEquals(expected, presented)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private fun httpPost(url: String, body: String) {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client().newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP " + response.code)
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
