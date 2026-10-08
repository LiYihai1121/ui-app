package com.ldp.adskip.net

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * 规则链路验签（ECDSA P-256 / SHA-256）。
 *
 * 威胁模型：明文 HTTP 的局域网链路上，攻击者可篡改规则响应、下发恶意规则
 * 驱动无障碍自动点击（如自动点「允许/安装」）。服务端用私钥对**响应原始
 * 字节**签名（`X-Rules-Signature` 头，base64 DER），本类用内置公钥验签，
 * 通过才允许规则落地——安全性不依赖传输层，天然兼容 HTTP。
 *
 * 算法选择 P-256 而非 Ed25519：`SHA256withECDSA` + `KeyFactory("EC")` 在
 * minSdk 26 全程可用；Ed25519 的 Java API 在低版本 Android 不可靠。
 *
 * fail-closed 契约：缺签名、验签失败、公钥/签名非法——一律返回 false，
 * 绝不放行未经验证的规则。
 */
object RulesSignature {

    /** 签名算法标识（与服务端 X-Rules-Signature-Alg 对齐） */
    const val ALG = "ecdsa-p256-sha256"

    /**
     * 校验规则响应签名。
     *
     * @param body 响应原始文本（按 UTF-8 字节校验，须与服务端签名的字节一致）
     * @param signatureB64 base64(DER) 签名；缺签名传 null
     * @param publicKeyB64 base64(X.509 SPKI) 公钥
     * @return 验签通过 true；其余一切情况 false
     */
    fun verify(body: String, signatureB64: String?, publicKeyB64: String): Boolean {
        if (signatureB64.isNullOrBlank() || publicKeyB64.isBlank()) return false
        return try {
            val decoder = Base64.getDecoder()
            val publicKey = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(decoder.decode(publicKeyB64)))
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(publicKey)
            signature.update(body.toByteArray(Charsets.UTF_8))
            signature.verify(decoder.decode(signatureB64))
        } catch (e: Exception) {
            // 密钥/签名格式非法同样按验签失败处理（fail-closed）
            false
        }
    }
}
