package com.ldp.adskip.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ldp.adskip.R
import com.ldp.adskip.device.Vendor
import com.ldp.adskip.ui.components.PageHeader
import com.ldp.adskip.ui.components.SectionCard
import com.ldp.adskip.ui.components.SectionHint
import com.ldp.adskip.ui.components.SectionTitle
import com.ldp.adskip.ui.components.StatTile

/**
 * 「我的」页：本机使用概览 + 应用与设备信息。
 *
 * 版式沿用 v3.1 重设计的约定：
 * - 页面自身**不**创建 `Scaffold`，inset 由外壳统一分发；
 * - 卡片走 [SectionCard]，标题走 [SectionTitle]，不各页自行 new `Card`；
 * - 状态（服务开/关）只用一种强调色表达，其余一律中性色。
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 从其他页切回时统计可能已变化（用户在别的页不会产生跳过，但服务状态会），
    // 进屏重读一次以保证与首页口径一致。
    LaunchedEffect(Unit) { viewModel.refreshStats() }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.profile_title),
            subtitle = stringResource(R.string.profile_subtitle)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { UsageCard(state = state) }
            item { AboutCard(state = state, vendorName = viewModel::vendorName) }

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
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatTile(
                value = state.totalSkips.toString(),
                label = stringResource(R.string.stats_total_label),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = state.activeAppCount.toString(),
                label = stringResource(R.string.profile_active_apps_label),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        SectionHint(
            stringResource(
                if (state.serviceRunning) R.string.status_on_hint else R.string.status_off_hint
            )
        )
    }
}

@Composable
private fun AboutCard(
    state: ProfileViewModel.UiState,
    vendorName: (Vendor) -> String
) {
    SectionCard {
        SectionTitle(stringResource(R.string.profile_about_section))
        Spacer(Modifier.height(8.dp))

        InfoRow(
            label = stringResource(R.string.profile_version_label),
            value = state.versionDisplay.ifBlank { stringResource(R.string.profile_value_unknown) }
        )
        InfoDivider()
        InfoRow(
            label = stringResource(R.string.profile_android_label),
            value = state.androidVersion.ifBlank { stringResource(R.string.profile_value_unknown) }
        )
        InfoDivider()
        InfoRow(
            label = stringResource(R.string.profile_device_label),
            value = state.deviceModel.ifBlank { stringResource(R.string.profile_value_unknown) }
        )
        InfoDivider()
        InfoRow(
            label = stringResource(R.string.profile_vendor_label),
            value = vendorName(state.vendor)
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
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun InfoDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
