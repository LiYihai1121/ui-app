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
import com.ldp.adskip.device.AccessibilityStatus
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
        /** 无障碍服务的有效状态（系统真值优先，查不到时为 UNKNOWN）。 */
        val accessibilityStatus: AccessibilityStatus = AccessibilityStatus.UNKNOWN,
        /** 应用版本，形如 `3.1 (10)`。 */
        val versionDisplay: String = "",
        /** Android 系统版本，如 `Android 15`。 */
        val androidVersion: String = "",
        /** 设备型号，如 `Pixel 7`。 */
        val deviceModel: String = "",
        /** 当前 ROM 厂商（复用 device/VendorKeepAlive 的识别结果，与设置页一致）。 */
        val vendor: Vendor = Vendor.GENERIC,
    ) {
        /** 服务已确认开启；UNKNOWN 不算开启，避免拿不确定的状态去承诺功能可用。 */
        val serviceRunning: Boolean get() = accessibilityStatus == AccessibilityStatus.ON
    }

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

        viewModelScope.launch {
            // 进程信号只作实时提示：用户在系统设置里关掉服务时回调可能迟迟不到，
            // 因此回屏时会用系统真值覆盖（见 refreshAccessibilityStatus）。
            AppEvents.serviceRunning.collect { running ->
                _uiState.value = _uiState.value.copy(
                    accessibilityStatus = AccessibilityStatus.decide(
                        systemEnabled = null,
                        processSignal = running,
                    ),
                )
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

    /**
     * 回屏时重查无障碍状态。
     *
     * 必须走系统真值：用户从本页跳去系统无障碍设置改完再返回，Service 的 `onDestroy`
     * 可能还没回调，只订阅进程信号会把过期的「运行中」显示下去。由 Screen 在进入本页时调用。
     */
    fun refreshAccessibilityStatus() {
        _uiState.value = _uiState.value.copy(
            accessibilityStatus = AccessibilityStatus.detect(
                context = container.app,
                serviceClassName = AccessibilityStatus.SKIP_AD_SERVICE_CLASS_NAME,
                processSignal = AppEvents.serviceRunningSnapshot,
            ),
        )
    }

    /** 厂商的可读名称（与设置页保活引导共用同一套文案）。 */
    fun vendorName(vendor: Vendor): String = container.app.getString(vendorLabelRes(vendor))

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AdskipApp
                ProfileViewModel(app.container)
            }
        }
    }
}
