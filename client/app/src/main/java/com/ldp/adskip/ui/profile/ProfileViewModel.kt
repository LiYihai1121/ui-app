package com.ldp.adskip.ui.profile

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ldp.adskip.AdskipApp
import com.ldp.adskip.AppContainer
import com.ldp.adskip.core.AppEvents
import com.ldp.adskip.device.PermissionInspector
import com.ldp.adskip.device.PermissionItem
import com.ldp.adskip.device.PermissionKeys
import com.ldp.adskip.device.PermissionLogic
import com.ldp.adskip.device.Vendor
import com.ldp.adskip.device.VendorKeepAlive
import com.ldp.adskip.ui.vendorLabelRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 「我的」页状态：本机使用概览 + 应用与设备信息。
 *
 * 边界遵守（ARCHITECTURE.md 2.1）：ui 层不 import `data.Prefs` / `net` / `sync` / `service`，
 * 统计经 [com.ldp.adskip.data.StatsRepository]、版本经
 * [com.ldp.adskip.data.SettingsRepository] 读取，UI 不自行查 `PackageManager`。
 *
 * 版本信息刻意不读 `BuildConfig`：约定插件未开启 `buildConfig`，该类并未生成。
 * 设备与系统信息是系统常量，进屏读一次；统计随 [AppEvents.skipped] 实时刷新。
 */
class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        /** 累计跳过次数。 */
        val totalSkips: Int = 0,
        /** 累计跳过过的应用数（按包名去重）。 */
        val activeAppCount: Int = 0,
        /** 当前是否已开启无障碍服务。 */
        val serviceRunning: Boolean = false,
        /** 应用版本，形如 `3.1 (10)`。 */
        val versionDisplay: String = "",
        /** Android 系统版本，如 `Android 15`。 */
        val androidVersion: String = "",
        /** 设备型号，如 `Pixel 7`。 */
        val deviceModel: String = "",
        /** 当前 ROM 厂商（复用 device/VendorKeepAlive 的识别结果，与设置页一致）。 */
        val vendor: Vendor = Vendor.GENERIC,
        /**
         * 各权限 / 系统开关的当前探测结果。
         *
         * 由 [refreshPermissions] 在进屏与每次 `ON_RESUME` 时重采——用户跳到系统设置
         * 改完再返回，必须重新探测，否则卡片会一直显示改动前的状态。
         */
        val permissions: List<PermissionItem> = emptyList(),
        /** 已就绪项数（仅统计「确定已开启」，UNKNOWN 不计入）。 */
        val permissionsReady: Int = 0,
        /** 是否存在未就绪项，决定汇总文案的语气。 */
        val permissionsNeedAttention: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        _uiState.value = _uiState.value.copy(
            versionDisplay = container.settingsRepo.appVersionDisplay(),
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            deviceModel = Build.MODEL,
            vendor = VendorKeepAlive.detect(),
        )
        refreshStats()
        refreshPermissions()

        viewModelScope.launch {
            AppEvents.serviceRunning.collect { running ->
                _uiState.value = _uiState.value.copy(serviceRunning = running)
            }
        }
        viewModelScope.launch {
            AppEvents.skipped.collect { refreshStats() }
        }
    }

    /** 重新读取统计；日志按时间倒序存储，故按包名去重即得「跳过过的应用数」。 */
    fun refreshStats() {
        val logs = container.statsRepo.logs()
        _uiState.value = _uiState.value.copy(
            totalSkips = container.statsRepo.total(),
            activeAppCount = logs.map { it.pkg }.distinct().size,
        )
    }

    /** 厂商的可读名称（与设置页保活引导共用同一套文案）。 */
    fun vendorName(vendor: Vendor): String = container.app.getString(vendorLabelRes(vendor))

    /**
     * 重新探测全部权限 / 系统开关状态。
     *
     * 幂等且无副作用，可安全地在每次 `ON_RESUME` 调用：
     * 权限状态的唯一变更来源在应用之外（系统设置页），本进程无法收到通知，
     * 只能靠「回到前台就重查」保证不陈旧——这是与磁贴
     * 「关闭是异步的、需延迟复查」同理的做法（见 SkipTileService 的 RESYNC_DELAY_MS）。
     */
    fun refreshPermissions() {
        val items = PermissionInspector.inspectAll(container.app)
        _uiState.value = _uiState.value.copy(
            permissions = items,
            permissionsReady = PermissionLogic.readyCount(items),
            permissionsNeedAttention = PermissionLogic.needsAttention(items),
        )
    }

    /** 单独读取某项权限状态；未知项返回 [com.ldp.adskip.device.PermissionState.UNKNOWN]。 */
    fun permission(key: String) = _uiState.value.permissions.firstOrNull { it.key == key }

    /** 厂商自启动项在当前 ROM 上是否适用（GENERIC ROM 无此开关）。 */
    fun supportsVendorKeepAlive(): Boolean = PermissionInspector.supportsVendorKeepAlive(_uiState.value.vendor)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AdskipApp
                ProfileViewModel(app.container)
            }
        }
    }
}
