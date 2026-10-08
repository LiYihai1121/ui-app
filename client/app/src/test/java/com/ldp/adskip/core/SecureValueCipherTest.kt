package com.ldp.adskip.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator

/**
 * 偏好值加密封装（AES-GCM）JVM 单测：密钥注入式设计使纯 JVM 可完整验证
 * 篡改/换钥/损坏等失败路径——fail-closed 契约在这里钉死。
 */
class SecureValueCipherTest {

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = SecureValueCipher(key)

    @Test
    fun `round trips every value type`() {
        val cases = listOf(
            StoredValue.Str("跳过广告"),
            StoredValue.Int(42),
            StoredValue.Long(1_700_000_000_000L),
            StoredValue.Float(3.5f),
            StoredValue.Bool(true),
            StoredValue.Bool(false),
            StoredValue.StrSet(setOf("跳过", "skip", "")),
            StoredValue.StrSet(emptySet()),
        )
        for (value in cases) {
            assertEquals(value, cipher.unseal(cipher.seal(value)))
        }
    }

    @Test
    fun `envelope hides plaintext and has stable prefix`() {
        val sealed = cipher.seal(StoredValue.Str("机密关键词"))
        assertTrue(sealed.startsWith("enc:v1:"))
        assertFalse(sealed.contains("机密关键词"))
    }

    @Test
    fun `tampered ciphertext fails closed`() {
        val sealed = cipher.seal(StoredValue.Str("原文"))
        // 篡改密文部分（GCM 验签必须失败）
        val parts = sealed.split(":")
        val ct = Base64.getDecoder().decode(parts[3])
        ct[0] = (ct[0].toInt() xor 0x01).toByte()
        val tampered = parts.subList(0, 3).joinToString(":") + ":" + Base64.getEncoder().encodeToString(ct)
        assertNull(cipher.unseal(tampered))
    }

    @Test
    fun `wrong key fails closed`() {
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val sealed = SecureValueCipher(other).seal(StoredValue.Int(1))
        assertNull(cipher.unseal(sealed))
    }

    @Test
    fun `malformed envelope fails closed`() {
        assertNull(cipher.unseal(""))
        assertNull(cipher.unseal("plaintext"))
        assertNull(cipher.unseal("enc:v1:only-one-part"))
        assertNull(cipher.unseal("enc:v1:@@@:###"))
        assertNull(cipher.unseal("v2:whatever"))
    }

    @Test
    fun `ciphertext differs for identical values`() {
        // 每次加密随机 IV：相同明文不产生相同密文（防模式推断）
        val a = cipher.seal(StoredValue.Str("same"))
        val b = cipher.seal(StoredValue.Str("same"))
        assertFalse(a == b)
    }
}
