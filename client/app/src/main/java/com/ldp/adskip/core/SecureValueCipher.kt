package com.ldp.adskip.core

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 偏好值加密封装（AES-256-GCM）：只做「任意偏好值 ↔ 密文信封」互转。
 *
 * 密钥由调用方注入——生产用 AndroidKeyStore 硬件密钥（不可导出），单测用
 * 普通 AES 密钥，因此本类可在纯 JVM 上完整验证（含篡改/换钥场景）。
 *
 * 信封格式：`enc:v1:<base64(iv)>:<base64(ciphertext)>`；密文明文为
 * `<type>|<payload>`（type ∈ s/i/l/b/ss，payload 编码见 encode/decode）。
 *
 * 失败策略（fail-closed）：解不出来一律返回 null——GCM 验签失败意味着
 * 篡改或换钥，格式错意味着损坏；此时宁可让调用方按「空值」处理这一条
 * 偏好，也不抛异常崩掉整个读取链路。
 */
internal class SecureValueCipher(private val key: SecretKey) {

    fun seal(value: StoredValue): String {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(encode(value).toByteArray(Charsets.UTF_8))
        return "enc:v1:${encoder.encodeToString(iv)}:${encoder.encodeToString(ct)}"
    }

    fun unseal(raw: String): StoredValue? {
        if (!raw.startsWith(PREFIX)) return null
        val parts = raw.removePrefix(PREFIX).split(":")
        if (parts.size != 2) return null
        return try {
            val iv = decoder.decode(parts[0])
            val ct = decoder.decode(parts[1])
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            decode(String(cipher.doFinal(ct), Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }

    /** 明文编码：`<type>|<payload>`。集合元素逐个 base64 后逗号连接（base64 无逗号，无歧义） */
    private fun encode(value: StoredValue): String = when (value) {
        is StoredValue.Str -> "s|" + value.value

        is StoredValue.Int -> "i|" + value.value

        is StoredValue.Long -> "l|" + value.value

        is StoredValue.Float -> "f|" + value.value

        is StoredValue.Bool -> "b|" + value.value

        is StoredValue.StrSet -> "ss|" + value.value.joinToString(",") {
            encoder.encodeToString(it.toByteArray(Charsets.UTF_8))
        }
    }

    private fun decode(plain: String): StoredValue? {
        val cut = plain.indexOf('|')
        if (cut < 0) return null
        val type = plain.substring(0, cut)
        val payload = plain.substring(cut + 1)
        return when (type) {
            "s" -> StoredValue.Str(payload)

            "i" -> payload.toIntOrNull()?.let { StoredValue.Int(it) }

            "l" -> payload.toLongOrNull()?.let { StoredValue.Long(it) }

            "f" -> payload.toFloatOrNull()?.let { StoredValue.Float(it) }

            "b" -> when (payload) {
                "true" -> StoredValue.Bool(true)
                "false" -> StoredValue.Bool(false)
                else -> null
            }

            "ss" -> try {
                if (payload.isEmpty()) {
                    StoredValue.StrSet(emptySet())
                } else {
                    StoredValue.StrSet(
                        payload.split(",").map { String(decoder.decode(it), Charsets.UTF_8) }.toSet(),
                    )
                }
            } catch (e: IllegalArgumentException) {
                null
            }

            else -> null
        }
    }

    companion object {
        private const val PREFIX = "enc:v1:"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private val encoder: Base64.Encoder = Base64.getEncoder()
        private val decoder: Base64.Decoder = Base64.getDecoder()
    }
}

/** 偏好存储的值类型（与 SharedPreferences 的类型集一一对应，含 API 36+ 的 Float） */
internal sealed class StoredValue {
    data class Str(val value: String) : StoredValue()

    // 嵌套类名 Int/Long/Float 会遮蔽同名内建类型，构造参数必须显式限定 kotlin.*
    data class Int(val value: kotlin.Int) : StoredValue()

    data class Long(val value: kotlin.Long) : StoredValue()
    data class Float(val value: kotlin.Float) : StoredValue()
    data class Bool(val value: Boolean) : StoredValue()
    data class StrSet(val value: Set<String>) : StoredValue()
}
