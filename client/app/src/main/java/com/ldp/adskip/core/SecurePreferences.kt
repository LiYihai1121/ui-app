package com.ldp.adskip.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * EncryptedSharedPreferences 的统一打开入口。
 *
 * 为什么收在 `core`：业务偏好的两处访问方（`data/Prefs` 与 `core/LanguagePreferences`）
 * 都要打开加密偏好。若各自直接 `EncryptedSharedPreferences.create`，会对同一个文件
 * 构造出两份 SP 句柄；这里按文件名进程内缓存单例，避免重复构建 MasterKey，也避免
 * 同一文件存在两个写入口。
 *
 * `core` 允许依赖 `androidx.*`（`ArchitectureBoundaryTest` 只禁止 `com.ldp.adskip.*`
 * 业务包），因此加密存储的构建逻辑可以横切共享，不产生循环依赖。
 *
 * security-crypto 1.1.x 已把 EncryptedSharedPreferences/MasterKey 标记为 deprecated，
 * 与仓库现状一致（`git log` 上既有加密实现同样使用该 API）；迁移 DataStore 属另行排期，
 * 此处集中收口以抑制重复告警。
 */
@Suppress("DEPRECATION")
object SecurePreferences {

    /** 应用业务偏好的统一加密文件名（Prefs 与 LanguagePreferences 共用，单一写入口）。 */
    const val APP_PREFS = "adskip_prefs_enc"

    private val instances = mutableMapOf<String, SharedPreferences>()

    @Synchronized
    fun open(context: Context, name: String = APP_PREFS): SharedPreferences {
        instances[name]?.let { return it }
        val created = EncryptedSharedPreferences.create(
            context,
            name,
            MasterKey.Builder(context).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        instances[name] = created
        return created
    }
}
