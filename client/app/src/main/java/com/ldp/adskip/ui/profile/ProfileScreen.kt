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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ldp.adskip.R
import com.ldp.adskip.device.KeepAliveNavigator
import com.ldp.adskip.device.Vendor
import com.ldp.adskip.ui.Messenger
import com.ldp.adskip.ui.components.PageHeader
import com.ldp.adskip.ui.components.SectionCard
import com.ldp.adskip.ui.components.SectionHint
import com.ldp.adskip.ui.components.SectionTitle
import com.ldp.adskip.ui.components.StatTile
import com.ldp.adskip.ui.settings.SettingsContent

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

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.profile_title),
            subtitle = stringResource(R.string.profile_subtitle),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { UsageCard(state = state) }
            // 设置（云端规则 / 免打扰 / 保活 / 磁贴）排在「关于」之前：
            // 它是用户会反复回来调整的高频项，而版本号、设备型号这类「关于」信息
            // 基本只会在遇到问题时才看。原来的顺序把高频项压在最长的滚动条末尾。
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
 * 首页的状态环已提供主行动按钮，这里是第二入口：服务被系统强杀后用户往往先落到
 * 「我的」页找设置，单独给出「打开无障碍设置」可少一次返回。
 *
 * 按钮用 tonal 而非 filled：首页已经把「去开启服务」做成全应用唯一的主行动，
 * 本页作为第二入口再用 filled 会在同一屏里出现两个同权重的 filled 按钮，
 * 把首页建立的优先级抹平。
 */
@Composable
private fun AccessibilityCard(running: Boolean, onOpen: () -> Unit) {
    SectionCard {
        SectionTitle(stringResource(R.string.profile_accessibility_section))
        Spacer(Modifier.height(8.dp))
        SectionHint(
            stringResource(
                if (running) R.string.status_on_hint else R.string.status_off_hint,
            ),
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_open_settings))
        }
    }
}

@Composable
private fun UsageCard(state: ProfileViewModel.UiState) {
    SectionCard {
        SectionTitle(stringResource(R.string.profile_usage_section))
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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
        Spacer(Modifier.height(8.dp))

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

        Spacer(Modifier.height(12.dp))
        SectionHint(stringResource(R.string.profile_privacy_hint))
    }
}

/** 「标签 — 取值」一行；取值过长时省略而非撑破布局。 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun InfoDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
