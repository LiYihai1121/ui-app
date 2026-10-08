package com.ldp.adskip.net

import com.ldp.adskip.net.HttpTransport.HttpStatusException
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * 网络层原语单测（FOLLOW-UP：SyncClient 无网络层单测，补 MockWebServer 覆盖）。
 *
 * [HttpTransport] 是纯 JVM 组件（OkHttp 封装），可直接经 MockWebServer 验证：
 * 200 / 304 / 非 2xx 不重试 / 连接错误指数退避重试 / 重试耗尽 / 读超时 / POST 载荷 / 证书锁定。
 */
class HttpTransportTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `GET 200 返回 body 且 notModified=false`() {
        server.enqueue(MockResponse().setBody("""{"keywords":["跳过"]}"""))
        val transport = HttpTransport.create(
            connectTimeoutMs = 3000,
            readTimeoutMs = 3000,
            maxRetries = 0,
        )

        val result = transport.getWithEtag(server.url("/api/v1/rules/latest").toString(), "")

        assertFalse(result.notModified)
        assertEquals("""{"keywords":["跳过"]}""", result.body)
    }

    @Test
    fun `If-None-Match 命中返回 304 notModified=true`() {
        server.enqueue(MockResponse().setResponseCode(304))
        val transport = HttpTransport.create(
            connectTimeoutMs = 3000,
            readTimeoutMs = 3000,
            maxRetries = 0,
        )

        val result = transport.getWithEtag(server.url("/api/v1/rules/latest").toString(), "sha256:abc")

        assertTrue(result.notModified)
        assertEquals("", result.body)
        // 校验 If-None-Match 头已发出
        assertEquals("sha256:abc", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `非 2xx 抛 HttpStatusException 且不重试`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val transport = HttpTransport.create(
            connectTimeoutMs = 3000,
            readTimeoutMs = 3000,
            maxRetries = 3, // 即使允许重试，HTTP 状态错误也不该重试
        )

        val e = assertThrows(HttpStatusException::class.java) {
            transport.getWithEtag(server.url("/api/v1/rules/latest").toString(), "")
        }
        assertEquals(500, e.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `连接阶段异常按指数退避重试，恢复后成功`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setBody("""{"retried":true}"""))
        val transport = HttpTransport.create(
            connectTimeoutMs = 1000,
            readTimeoutMs = 1000,
            maxRetries = 2,
            baseBackoffMs = 1, // 测试毫秒级退避
        )

        val result = transport.getWithEtag(server.url("/api/v1/rules/latest").toString(), "")

        assertFalse(result.notModified)
        assertEquals("""{"retried":true}""", result.body)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `重试耗尽后抛 IOException`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val transport = HttpTransport.create(
            connectTimeoutMs = 500,
            readTimeoutMs = 500,
            maxRetries = 1,
            baseBackoffMs = 1,
        )

        assertThrows(IOException::class.java) {
            transport.getWithEtag(server.url("/api/v1/rules/latest").toString(), "")
        }
    }

    @Test
    fun `读超时抛 IOException`() {
        server.enqueue(
            MockResponse()
                .setBody("慢响应")
                .setBodyDelay(
                    2000,
                    TimeUnit.MILLISECONDS,
                ),
        )
        val transport = HttpTransport.create(
            connectTimeoutMs = 500,
            readTimeoutMs = 300,
            maxRetries = 0,
        )

        assertThrows(IOException::class.java) {
            transport.getWithEtag(server.url("/api/v1/rules/latest").toString(), "")
        }
    }

    @Test
    fun `POST JSON 载荷与 Content-Type 正确`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val transport = HttpTransport.create(
            connectTimeoutMs = 3000,
            readTimeoutMs = 3000,
            maxRetries = 0,
        )

        transport.postJson(server.url("/api/skip").toString(), """{"pkg":"com.x","label":"跳过"}""")

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.getHeader("Content-Type").orEmpty().contains("application/json"))
        assertEquals("""{"pkg":"com.x","label":"跳过"}""", recorded.body.readUtf8())
    }

    // ---------------------------- TLS 证书锁定 ----------------------------

    /**
     * 构建一个 TLS 模式的服务端与一个「信任该服务端证书」的客户端，并渲染指定 pin。
     *
     * 注意 host 必须动态取 [MockWebServer.url] 的 host：不同平台的 loopback 解析不同
     * （Windows 可能返回 `127.0.0.1`，Linux CI 返回 `localhost`），证书 SAN 须同时覆盖
     * DNS `localhost` 与 IPv4/IPv6 loopback，否则 hostname 校验会因平台不同而偶发失败。
     * pin 同样绑定动态 host，确保证书锁定真正生效。
     */
    private fun buildTls(makePin: (pinnedCert: HeldCertificate) -> String): ForTestingTls {
        val heldCert = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost") // DNS SAN：URL host 为 localhost 的平台
            .addSubjectAlternativeName("127.0.0.1") // IP SAN：OkHttp 对 IP 主机会校验 IP SAN
            .addSubjectAlternativeName("::1") // IPv6 loopback：部分平台 localhost 解析为 ::1
            .build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(heldCert).build()
        val tlsServer = MockWebServer()
        tlsServer.useHttps(serverTls.sslSocketFactory(), false)
        tlsServer.start()
        val tlsUrl = tlsServer.url("/api/v1/health")
        val pinHost = tlsUrl.host
        val clientTls = HandshakeCertificates.Builder()
            .addTrustedCertificate(heldCert.certificate)
            .build()
        val correctPin = makePin(heldCert)
        return ForTestingTls(
            server = tlsServer,
            url = tlsUrl.toString(),
            makeClient = {
                OkHttpClient.Builder()
                    .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
                    .certificatePinner(
                        CertificatePinner.Builder()
                            .add(pinHost, correctPin)
                            .build(),
                    )
                    .build()
            },
            onClient = { client -> HttpTransport.forTesting(client, maxRetries = 0) },
        )
    }

    data class ForTestingTls(
        val server: MockWebServer,
        val url: String,
        val makeClient: () -> OkHttpClient,
        val onClient: (OkHttpClient) -> HttpTransport,
    )

    @Test
    fun `证书锁定：错误 pin 拒绝 TLS 请求`() {
        val fixture = buildTls(
            makePin = { _ -> "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" },
        )
        fixture.server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        try {
            val transport = fixture.onClient(fixture.makeClient())
            assertThrows(SSLException::class.java) {
                transport.getWithEtag(fixture.url, "")
            }
        } finally {
            fixture.server.shutdown()
        }
    }

    @Test
    fun `证书锁定：正确 pin 通过 TLS 请求`() {
        val fixture = buildTls(
            makePin = { heldCert -> CertificatePinner.pin(heldCert.certificate) },
        )
        fixture.server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        try {
            val result = fixture.onClient(fixture.makeClient()).getWithEtag(fixture.url, "")
            assertFalse(result.notModified)
            assertEquals("""{"status":"ok"}""", result.body)
        } finally {
            fixture.server.shutdown()
        }
    }
}
