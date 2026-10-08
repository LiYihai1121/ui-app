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
     * 在同一端口上换 pin 需要重建 server（MockWebServer 单次即为固定 TLS 上下文），
     * 故这里返回可复用的 [ForTestingTls]（含 client 构造器），避免重复样板。
     */
    private fun buildTls(renderPin: (String) -> String, onClient: (OkHttpClient) -> HttpTransport): ForTestingTls {
        val heldCert = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("127.0.0.1") // IP SAN：OkHttp 对 IP 主机会校验 IP SAN
            .build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(heldCert).build()
        val tlsServer = MockWebServer()
        tlsServer.useHttps(serverTls.sslSocketFactory(), false)
        tlsServer.start()
        val clientTls = HandshakeCertificates.Builder()
            .addTrustedCertificate(heldCert.certificate)
            .build()
        return ForTestingTls(
            server = tlsServer,
            url = tlsServer.url("/api/v1/health").toString(),
            makeClient = { pin ->
                OkHttpClient.Builder()
                    .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
                    .certificatePinner(
                        CertificatePinner.Builder()
                            .add("127.0.0.1", renderPin(pin))
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
        val makeClient: (String) -> OkHttpClient,
        val onClient: (OkHttpClient) -> HttpTransport,
    )

    @Test
    fun `证书锁定：错误 pin 拒绝 TLS 请求`() {
        val fixture = buildTls(
            renderPin = { _ -> "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" },
            onClient = { c -> HttpTransport.forTesting(c, maxRetries = 0) },
        )
        fixture.server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        try {
            val transport = fixture.onClient(fixture.makeClient("ignored"))
            assertThrows(SSLException::class.java) {
                transport.getWithEtag(fixture.url, "")
            }
        } finally {
            fixture.server.shutdown()
        }
    }

    @Test
    fun `证书锁定：正确 pin 通过 TLS 请求`() {
        val heldCert = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(heldCert).build()
        val tlsServer = MockWebServer()
        tlsServer.useHttps(serverTls.sslSocketFactory(), false)
        tlsServer.start()
        val clientTls = HandshakeCertificates.Builder()
            .addTrustedCertificate(heldCert.certificate)
            .build()
        val correctPin = CertificatePinner.pin(heldCert.certificate)
        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .certificatePinner(
                CertificatePinner.Builder()
                    .add("127.0.0.1", correctPin)
                    .build(),
            )
            .build()
        val transport = HttpTransport.forTesting(client, maxRetries = 0)
        tlsServer.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        try {
            val result = transport.getWithEtag(tlsServer.url("/api/v1/health").toString(), "")
            assertFalse(result.notModified)
            assertEquals("""{"status":"ok"}""", result.body)
        } finally {
            tlsServer.shutdown()
        }
    }
}
