package com.ldp.adskip.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/**
 * 规则验签 JVM 单测（ECDSA P-256）：
 * 合法签名放行；篡改/缺签名/换钥/坏格式一律 fail-closed——
 * 验签是规则落地前的硬门槛，任何含糊都等于 MITM 注入口。
 */
class RulesSignatureTest {

    private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private val pubB64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    private fun sign(body: String): String {
        val s = Signature.getInstance("SHA256withECDSA")
        s.initSign(keyPair.private)
        s.update(body.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(s.sign())
    }

    @Test
    fun `valid signature verifies`() {
        val body = """{"rules":{"globalKeywords":["跳过"]}}"""
        assertTrue(RulesSignature.verify(body, sign(body), pubB64))
    }

    @Test
    fun `tampered body fails`() {
        val body = """{"rules":{"globalKeywords":["跳过"]}}"""
        val sig = sign(body)
        val tampered = body.replace("跳过", "允许")
        assertFalse(RulesSignature.verify(tampered, sig, pubB64))
    }

    @Test
    fun `missing signature fails`() {
        assertFalse(RulesSignature.verify("{}", null, pubB64))
        assertFalse(RulesSignature.verify("{}", "", pubB64))
    }

    @Test
    fun `signature from another key fails`() {
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val s = Signature.getInstance("SHA256withECDSA")
        s.initSign(other.private)
        s.update("{}".toByteArray(Charsets.UTF_8))
        val sig = Base64.getEncoder().encodeToString(s.sign())
        assertFalse(RulesSignature.verify("{}", sig, pubB64))
    }

    @Test
    fun `malformed key or signature fails`() {
        val body = "{}"
        assertFalse(RulesSignature.verify(body, sign(body), "not-a-der-key!!"))
        assertFalse(RulesSignature.verify(body, "###", pubB64))
        assertFalse(RulesSignature.verify(body, sign(body), ""))
    }
}
