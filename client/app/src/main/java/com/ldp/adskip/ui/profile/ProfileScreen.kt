package com.ldp.adskip.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ldp.adskip.R
import com.ldp.adskip.device.KeepAliveNavigator
import com.ldp.adskip.device.PermissionKeys
import com.ldp.adskip.device.Vendor
import com.ldp.adskip.ui.Messenger
import com.ldp.adskip.ui.components.InfoDivider
import com.ldp.adskip.ui.components.InfoRow
import com.ldp.adskip.ui.components.PageHeader
import com.ldp.adskip.ui.components.SectionCard
import com.ldp.adskip.ui.components.SectionHint
import com.ldp.adskip.ui.components.SectionTitle
import com.ldp.adskip.ui.components.StatTile
import com.ldp.adskip.ui.settings.SettingsContent
import com.ldp.adskip.ui.theme.Spacing

/**
 * 「我的」页：本机使用概览 + 无障碍入口 + 设置 + 应用与设备信息。
 *
 * 自 v3.2 起本页取代原独立的「设置」一级页面（底部导航由 5 项收敛为 4 项），
 * 依据是设置与「关于本机」本就属同一心智模型，分成两页会让用户为改一个
 * 免打扰时段而去名为「设置」的第三个页签。设置内容经 [SettingsContent] 内嵌，
 * 复用其 ViewModel 与全部既有逻辑，不做复制。
 *
 * 版式沿用 v3.1 重设计的约定：
 * - 页面自身**不**创建 `Scaffold`，inset 由外壳统一分发；
 * - 卡片走 [SectionCard]，标题走 [SectionTitle]，不各页自行 new `Card`；
 * - 状态（服务开/关）只用一种强调色表达，其余一律中性色。
 */
@Composable
fun ProfileScreen(messenger: Messenger, viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tileLabel = stringResource(R.string.tile_label)

    // 每次回到前台都重读：统计可能已变化，且用户可能刚跳去系统无障碍设置改了开关。
    // 用 ON_RESUME 而非 LaunchedEffect(Unit)——后者只在首次进入组合时跑一次，
    // 从系统设置返回时 Activity 并未重建，过期状态会被一直显示下去。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshAccessibilityStatus()
        viewModel.refreshStats()
    }

    /*
     * 权限清单各行的跳转出口。
     *
     * 统一经 device/KeepAliveNavigator（系统入口的唯一出口，含可解析性探测与降级），
     * UI 不自行拼 Intent。失败时给出「手动路径」提示，而不是静默无事发生。
     */
    val onOpenPermission: (String) -> Unit = { key ->
        val vendorName = viewModel.vendorName(state.vendor)
        when (key) {
            PermissionKeys.ACCESSIBILITY -> {
                if (!KeepAliveNavigator.openAccessibilitySettings(context)) {
                    messenger.show(context.getString(R.string.settings_open_failed))
                }
            }

            PermissionKeys.BATTERY -> {
                if (!KeepAliveNavigator.openBatteryOptimizationSettings(context)) {
                    messenger.show(context.getString(R.string.settings_open_failed))
                }
            }

            PermissionKeys.VENDOR_KEEPALIVE -> {
                val opened = KeepAliveNavigator.openKeepAliveSettings(context) != null
                val res = if (opened) {
                    R.string.settings_keepalive_opened
                } else {
                    R.string.settings_keepalive_failed
                }
                messenger.show(context.getString(res, vendorName))
            }

            PermissionKeys.QUICK_TILE -> {
                // 低版本无法主动请求添加，退化为文案引导（文案已含手动路径）。
                messenger.show(context.getString(R.string.settings_tile_manual, tileLabel))
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.profile_title),
            subtitle = stringResource(R.string.profile_subtitle),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            /*
             * 「我的」页承载四类互不相干的内容，顺序按**用户来访目的**排：
             *
             * 1. 权限与系统开关 —— 最高优先。它回答「这个应用到底工作了吗」，
             *    是唯一会阻塞全部功能的内容；权限没配好，下面三项都没有意义。
             * 2. 使用概览 —— 「它替我跳过了多少」，功能生效的正反馈。
             * 3. 设置 —— 用户会反复回来调整的高频项（云端规则 / 免打扰）。
             * 4. 关于 —— 版本号、设备型号，基本只在遇到问题时才看，放最后。
             *
             * 此前的顺序把权限卡排在设置之后：一个「还没配好」的权限清单被压在两屏
             * 之外，而设置项在功能未生效时几乎都是无效配置。
             */
            item {
                PermissionCard(
                    items = state.permissionItems,
                    readyCount = state.permissionReadyCount,
                    onOpen = { key -> onOpenPermission(key) },
                )
            }
            item { UsageCard(state = state) }
            item { SettingsContent(messenger) }
            item {
                AccessibilityCard(
                    running = state.serviceRunning,
                    onOpen = {
                        // 系统入口统一经 device/ 层探测可解析性后降级，UI 不自行拼 Intent
                        if (!KeepAliveNavigator.openAccessibilitySettings(context)) {
                            messenger.show(context.getString(R.string.settings_open_failed))
                        }
                    },
                )
            }
            item { AboutCard(state = state, vendorName = viewModel::vendorName) }
        }
    }
}

/**
 * 无障碍服务入口卡片。
 *
 * 与权限清单的分工：清单里的「无障碍服务」行只报**状态**（并且刻意不带行内按钮），
 * 本卡片是它唯一的**行动入口**，服务被系统强杀后用户落到「我的」页可少一次返回。
 *
 * 这里只显示状态结论（「服务未开启 / 服务运行中」）。早期它还重复渲染整句
 * `status_off_hint`，与清单行的状态描述叠在同一屏讲同一件事；去掉重复后，
 * 清单行 → 本卡片的视觉动线变成「是什么状态 → 怎么改变它」。
 *
 * 按钮用 tonal 而非 filled：首页已经把「去开启服务」做成全应用唯一的主行动，
 * 本页作为第二入口再用 filled 会在同一屏里出现两个同权重的 filled 按钮，
 * 把首页建立的优先级抹平。
 */
@Composable
private fun AccessibilityCard(running: Boolean, onOpen: () -> Unit) {
    SectionCard {
        SectionTitle(
            stringResource(if (running) R.string.status_on else R.string.status_off),
        )
        Spacer(Modifier.height(Spacing.lg))
        FilledTonalButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_open_settings))
        }
    }
}

@Composable
private fun UsageCard(state: ProfileViewModel.UiState) {
    SectionCard {
        SectionTitle(stringResource(R.string.profile_usage_section))
        Spacer(Modifier.height(Spacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            StatTile(
                value = state.totalSkips.toString(),
                label = stringResource(R.string.stats_total_label),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = state.activeAppCount.toString(),
                label = stringResource(R.string.profile_active_apps_label),
                modifier = Modifier.weight(1f),
            )
        }
        // 此处不再重复「服务运行中 / 未开启」的状态提示：同一句话在首页状态卡已经
        // 出现过，页脚再写一遍只会让概览卡变长而不增加信息。服务状态属于「做什么」
        // 而不是「用得怎么样」，后者才是概览该回答的。
    }
}

@Composable
private fun AboutCard(state: ProfileViewModel.UiState, vendorName: (Vendor) -> String) {
    SectionCard {
        SectionTitle(stringResource(R.string.profile_about_section))
        Spacer(Modifier.height(Spacing.sm))

        InfoRow(
            label = stringResource(R.string.profile_version_label),
            value = state.versionDisplay.ifBlank { stringResource(R.string.profile_value_unknown) },
        )
        InfoDivider()
        InfoRow(
            label = stringResource(R.string.profile_android_label),
            value = state.androidVersion.ifBlank { stringResource(R.string.profile_value_unknown) },
        )
        InfoDivider()
        InfoRow(
            label = stringResource(R.string.profile_device_label),
            value = state.deviceModel.ifBlank { stringResource(R.string.profile_value_unknown) },
        )
        InfoDivider()
        InfoRow(
            label = stringResource(R.string.profile_vendor_label),
            value = vendorName(state.vendor),
        )

        Spacer(Modifier.height(Spacing.md))
        SectionHint(stringResource(R.string.profile_privacy_hint))
    }
}
