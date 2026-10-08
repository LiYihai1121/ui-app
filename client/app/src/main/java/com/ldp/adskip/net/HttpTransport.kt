package com.ldp.adskip.net

import okhttp3.CertificatePinner
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 网络原语收口（FOLLOW-UP P0 证书锁定 / P3 连接池、Keep-Alive、重试）。
 *
 * 替代之前的裸 `HttpURLConnection`，全部网络细节收口于 [OkHttpClient]：
 * - 连接池 + Keep-Alive：默认 5 路 / 5 分钟空闲回收（[ConnectionPool] 默认值显式声明）；
 * - GET 指数退避重试（幂等安全）；POST 不重试（避免重复上报副作用）；
 * - 证书锁定：配置了 `host->pin`（SHA-256 公钥指纹）时，OKHttp 的 [CertificatePinner]
 *   会对匹配主机强制校验证书公钥，防中间人。LAN 自建服务的证书指纹由部署方确认后
 *   填入 [create] 的 `hostPins`（例如 `"your-host" to listOf("sha256/XXXX...")`）。
 *
 * 纯 JVM 可测（网络层单测用 MockWebServer，见 `net/HttpTransportTest`）。
 */
class HttpTransport private constructor(
    private val client: OkHttpClient,
    private val maxRetries: Int,
    private val baseBackoffMs: Long,
) {

    /** GET 结果：body + 是否命中 If-None-Match（304）。 */
    data class GetResult(val body: String, val notModified: Boolean)

    /** GET 带 If-None-Match。304 → notModified=true；非 2xx → 抛 [HttpStatusException]。 */
    fun getWithEtag(url: String, etag: String): GetResult {
        var attempt = 0
        while (true) {
            try {
                val request = Request.Builder().url(url).apply {
                    if (etag.isNotEmpty()) header("If-None-Match", etag)
                }.get().build()
                return client.newCall(request).execute().use { resp ->
                    when (resp.code) {
                        304 -> GetResult("", true)
                        in 200..299 -> GetResult(resp.body?.string().orEmpty(), false)
                        else -> throw HttpStatusException(resp.code)
                    }
                }
            } catch (e: IOException) {
                // 网络层失败且尚有重试额度：指数退避后重试；HTTP 状态错误不在 IOException 之列，直接上抛
                if (attempt >= maxRetries) throw e
                attempt++
                Thread.sleep(baseBackoffMs * (1L shl (attempt - 1)))
            }
        }
    }

    /** POST JSON 并发送；不检查响应状态（上报静默失败语义）。 */
    fun postJson(url: String, body: String) {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { }
    }

    /**
     * HTTP 状态错误（非 2xx、非 304）。不继承 [IOException]：重试逻辑只针对连接层故障，
     * 状态错误代表「服务端已应答」，重试无益（且 POST 会重复副作用）。
     */
    class HttpStatusException(val code: Int) : Exception("HTTP $code")

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun create(
            connectTimeoutMs: Long,
            readTimeoutMs: Long,
            maxRetries: Int = DEFAULT_MAX_RETRIES,
            baseBackoffMs: Long = DEFAULT_BASE_BACKOFF_MS,
            hostPins: Map<String, List<String>> = emptyMap(),
        ): HttpTransport {
            val builder = OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                // 连接池 + Keep-Alive（FOLLOW-UP P3：避免每请求新建连接）
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))

            if (hostPins.isNotEmpty()) {
                val pinnerBuilder = CertificatePinner.Builder()
                for ((host, pins) in hostPins) {
                    pinnerBuilder.add(host, *pins.toTypedArray())
                }
                builder.certificatePinner(pinnerBuilder.build())
            }

            return HttpTransport(builder.build(), maxRetries, baseBackoffMs)
        }

        /** 测试专用：注入自定义 OkHttpClient（MockWebServer / 自签 TLS / pin 用例），不走 [create] 默认配置。 */
        internal fun forTesting(
            client: OkHttpClient,
            maxRetries: Int = DEFAULT_MAX_RETRIES,
            baseBackoffMs: Long = DEFAULT_BASE_BACKOFF_MS,
        ): HttpTransport = HttpTransport(client, maxRetries, baseBackoffMs)

        private const val DEFAULT_MAX_RETRIES = 2
        private const val DEFAULT_BASE_BACKOFF_MS = 500L
    }
}
