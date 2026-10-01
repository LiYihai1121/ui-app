package com.ldp.adskip.ui.profile

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ldp.adskip.R
import com.ldp.adskip.device.KeepAliveNavigator
import com.ldp.adskip.device.PermissionInspector
import com.ldp.adskip.device.TileAddResult
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

    // 从其他页切回时统计可能已变化（用户在别的页不会产生跳过，但服务状态会），
    // 进屏重读一次以保证与首页口径一致。
    LaunchedEffect(Unit) { viewModel.refreshStats() }

    // 权限状态**只在应用外可改**，本进程收不到任何通知，只能「回到前台就重查」。
    //
    // 这是本卡片存在的必要条件：用户点「去设置」跳到系统页改完返回，若不复查就会看到
    // 改动前的旧状态，于是反复跳转、反复怀疑是不是没生效。设置页的电池优化按钮此前
    // 就踩在这个坑里（只有 LaunchedEffect(Unit) 首次组合时读一次）。
    //
    // 用 LifecycleResumeEffect 而非 LaunchedEffect：后者只在首次组合跑一次，
    // 页面在 LazyColumn 中被回收再重建时也不会重跑。RESUME 同时覆盖
    // 「跳系统设置再返回」与「切到别的 tab 再回来」两种路径。
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
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
            item { UsageCard(state = state) }
            // 设置（云端规则 / 免打扰 / 保活 / 磁贴）排在「关于」之前：
            // 它是用户会反复回来调整的高频项，而版本号、设备型号这类「关于」信息
            // 基本只会在遇到问题时才看。原来的顺序把高频项压在最长的滚动条末尾。
            item { SettingsContent(messenger) }
            item {
                // 权限清单排在设置之后、「关于」之前：它回答的是「为什么还不生效」，
                // 与设置项同属排查路径，但比静态的版本/型号信息更需要被看到。
                PermissionCard(
                    items = state.permissions,
                    readyCount = state.permissionsReady,
                    needAttention = state.permissionsNeedAttention,
                    supportsKeepAlive = viewModel.supportsVendorKeepAlive(),
                    canRequestTile = PermissionInspector.supportsQuickTileRequest(),
                    onOpenAccessibility = {
                        if (!KeepAliveNavigator.openAccessibilitySettings(context)) {
                            messenger.show(context.getString(R.string.settings_open_failed))
                        }
                    },
                    onOpenBattery = {
                        if (!KeepAliveNavigator.openBatteryOptimizationSettings(context)) {
                            messenger.show(context.getString(R.string.settings_open_failed))
                        }
                    },
                    onOpenKeepAlive = {
                        // 复用设置页的既有反馈（含厂商名与手动路径兜底提示），不另写一套文案
                        if (KeepAliveNavigator.openKeepAliveSettings(context) == null) {
                            messenger.show(
                                context.getString(
                                    R.string.settings_keepalive_failed,
                                    viewModel.vendorName(state.vendor),
                                ),
                            )
                        }
                    },
                    onRequestTile = {
                        if (!KeepAliveNavigator.canRequestAddTile()) {
                            messenger.show(
                                context.getString(
                                    R.string.settings_tile_manual,
                                    context.getString(R.string.tile_label),
                                ),
                            )
                        } else {
                            KeepAliveNavigator.requestAddTile(
                                context = context,
                                label = context.getString(R.string.tile_label),
                            ) { messenger.show(tileResultText(context, it)) }
                        }
                    },
                )
            }
            item { AboutCard(state = state, vendorName = viewModel::vendorName) }
        }
    }
}

/**
 * 把 [com.ldp.adskip.device.TileAddResult] 映射为提示文案。
 *
 * 与 `SettingsViewModel.tileAddMessage` 同源同文案；放在此处是因为卡片是权限视角的入口，
 * 那里持有的是设置视角的 ViewModel，两处不共享实例，只能各自映射一次（文案引用同一批
 * string 资源，故不会出现「一处更新一处没更新」）。
 */
private fun tileResultText(context: Context, result: TileAddResult): String {
    val label = context.getString(R.string.tile_label)
    return when (result) {
        TileAddResult.ADDED -> context.getString(R.string.settings_tile_added)
        TileAddResult.ALREADY_ADDED -> context.getString(R.string.settings_tile_exists)
        TileAddResult.NOT_FOREGROUND -> context.getString(R.string.settings_tile_not_foreground)
        TileAddResult.FAILED -> context.getString(R.string.settings_tile_manual, label)
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
