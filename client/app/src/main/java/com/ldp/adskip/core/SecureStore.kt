package com.ldp.adskip.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 加密偏好存储的**唯一**入口（横切设施，`core` 层）。
 *
 * 为什么在 `core`：偏好要被 `data/Prefs`（业务键值）与 `core/LanguagePreferences`
 * （语言选择，`ui/` 与 `device/` 都要读）共同使用；`core` 不得依赖业务包，
 * 所以「加密 SP 的创建 + 明文历史迁移」只能落在这里，两侧各自持有自己的键。
 *
 * 安全契约：
 * - 所有偏好一律走 [prefs]（AES256_SIV 键 / AES256_GCM 值，MasterKey 存于 AndroidKeystore）；
 * - 历史明文文件 `adskip_prefs` 在首次访问时**全量迁移**后删除源文件，
 *   迁移幂等：源为空即跳过，进程重启不会重复迁移；
 * - 旧语言偏好与旧业务偏好曾共用同一个明文文件，迁移是全量键值拷贝，
 *   因此语言键（`language`）与业务键一起进入加密存储，不会因源文件删除而丢失。
 */
object SecureStore {

    /** 旧明文存储文件名（迁移源；迁移完成后删除）。 */
    private const val LEGACY_SP_NAME = "adskip_prefs"

    /** 加密存储文件名。 */
    private const val SP_NAME = "adskip_prefs_enc"

    private var spInstance: SharedPreferences? = null

    fun prefs(context: Context): SharedPreferences {
        spInstance?.let { return it }
        synchronized(this) {
            spInstance?.let { return it }
            val encrypted = EncryptedSharedPreferences.create(
                context,
                SP_NAME,
                MasterKey.Builder(context).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            migrateLegacy(context, encrypted)
            spInstance = encrypted
            return encrypted
        }
    }

    private fun migrateLegacy(context: Context, target: SharedPreferences) {
        val legacy = context.getSharedPreferences(LEGACY_SP_NAME, Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) return
        val editor = target.edit()
        for ((key, value) in legacy.all) {
            when (value) {
                is String -> editor.putString(key, value)

                is Int -> editor.putInt(key, value)

                is Long -> editor.putLong(key, value)

                is Boolean -> editor.putBoolean(key, value)

                else -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
            }
        }
        editor.apply()
        // 删除明文源文件 = 迁移完成标记（跨进程重启幂等，替代进程内 migrated 标志）
        runCatching { context.deleteSharedPreferences(LEGACY_SP_NAME) }
    }
}
