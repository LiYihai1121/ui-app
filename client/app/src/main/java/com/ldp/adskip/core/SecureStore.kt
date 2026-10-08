package com.ldp.adskip.core

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 加密偏好存储的唯一入口（横切设施，core 层）。
 *
 * 机制：普通 SharedPreferences 作容器 + [SecureValueCipher] 逐值加密，
 * 密钥存于 AndroidKeyStore（AES-256-GCM，硬件保护、不可导出）。
 * 替代已废弃的 `androidx.security:security-crypto`（其 Tink 格式存量数据
 * 经 [LegacyEncryptedPrefsMigration] 一次性迁移后下版本删除）。
 *
 * 为什么在 core：偏好被 `data/Prefs`（业务键值）与 `core/LanguagePreferences`
 * （语言选择，ui 与 device 都要读）共同使用；core 不得依赖业务包，故存储
 * 机制只能落在这里，两侧各自持有自己的键。
 *
 * **存储收口**：全应用只有这里允许 `getSharedPreferences`（`SecureStorageContractTest`
 * 强制）——历史上 `LanguagePreferences` 与 `data/Prefs` 曾共用同一明文文件名，
 * 迁移清源时把语言设置连带销毁；收口到单一存储后不存在第二份真相与同名互踩。
 *
 * 迁移（一次性、幂等、先落盘后清源）：
 * 1. Tink 旧库（v3.2.0 起的权威副本）→ 新存储；
 * 2. 明文旧库 `adskip_prefs` **压在上面**——它虽在 v3.2.0 迁移时被清空，
 *    但语言键一直由旧 LanguagePreferences 写在里面，比 Tink 副本更新。
 */
object SecureStore {

    /** 新加密存储文件名（键名明文 + 值密文信封） */
    private const val STORE_NAME = "adskip_secure_store"

    /** 明文旧存储（迁移源，含历史上一直写入的语言键） */
    private const val LEGACY_PLAIN_NAME = "adskip_prefs"

    private const val KEY_ALIAS = "adskip_secure_store_v1"

    private var instance: SharedPreferences? = null

    fun prefs(context: Context): SharedPreferences {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val secure = SecureSharedPreferences(
                context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE),
                SecureValueCipher(keystoreKey()),
            )
            migrateLegacy(context, secure)
            instance = secure
            return secure
        }
    }

    private fun migrateLegacy(context: Context, target: SharedPreferences) {
        // 先迁 Tink 旧库（批量），再迁明文旧库压上；每步写入落盘后才清源，
        // 中途失败下次启动重新合并（源未清、结果一致），不会丢数据。
        val tink = LegacyEncryptedPrefsMigration.readAll(context)
        if (!tink.isNullOrEmpty()) {
            if (target.putAll(tink)) LegacyEncryptedPrefsMigration.clear(context)
        }
        val plain = context.getSharedPreferences(LEGACY_PLAIN_NAME, Context.MODE_PRIVATE)
        if (plain.all.isNotEmpty()) {
            if (target.putAll(plain.all)) {
                runCatching { context.deleteSharedPreferences(LEGACY_PLAIN_NAME) }
            }
        }
    }

    private fun SharedPreferences.putAll(values: Map<String, Any?>): Boolean {
        val editor = edit()
        for ((key, value) in values) {
            when (value) {
                is String -> editor.putString(key, value)

                is Int -> editor.putInt(key, value)

                is Long -> editor.putLong(key, value)

                is Float -> editor.putFloat(key, value)

                is Boolean -> editor.putBoolean(key, value)

                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
            }
        }
        return editor.commit()
    }

    /** AndroidKeyStore 硬件密钥（AES-256-GCM）；不存在则生成一次 */
    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
