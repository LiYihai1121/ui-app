package com.ldp.adskip.core

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 一次性迁移器：读取 security-crypto（Tink）格式的旧加密偏好，供
 * [SecureStore] 合并进新的 AndroidKeyStore 加密存储。
 *
 * **迁移期依赖**：`androidx.security:security-crypto` 已被上游废弃，
 * 仅此文件允许引用（`SecureStorageContractTest` 强制）；v3.2.0 已发布
 * 用户的存量数据是 Tink 格式，没有这个库就读不出来，因此本文件与依赖
 * 在完成迁移使命后的**下个版本整体删除**。
 */
internal object LegacyEncryptedPrefsMigration {

    private const val LEGACY_TINK_NAME = "adskip_prefs_enc"

    /** 读取旧加密存储全部键值；不存在/损坏/解不开一律返回 null（无数据可迁） */
    fun readAll(context: Context): Map<String, Any?>? {
        val prefs = try {
            EncryptedSharedPreferences.create(
                context,
                LEGACY_TINK_NAME,
                MasterKey.Builder(context).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            return null
        }
        return try {
            prefs.all.filterValues { it != null }
        } catch (e: Exception) {
            null
        }
    }

    /** 迁移完成后清掉旧存储（含 Tink 密钥元数据文件） */
    fun clear(context: Context) {
        runCatching { context.deleteSharedPreferences(LEGACY_TINK_NAME) }
    }
}
