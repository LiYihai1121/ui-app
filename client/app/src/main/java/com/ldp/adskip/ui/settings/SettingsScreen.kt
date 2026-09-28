package com.ldp.adskip.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ldp.adskip.R
import com.ldp.adskip.device.KeepAliveNavigator
import com.ldp.adskip.ui.Messenger
import com.ldp.adskip.ui.UiEffect
import com.ldp.adskip.ui.components.LabeledSwitch
import com.ldp.adskip.ui.components.SectionCard
import com.ldp.adskip.ui.components.SectionHint
import com.ldp.adskip.ui.components.SectionTitle
import com.ldp.adskip.ui.vendorLabelRes
import java.util.Locale

/**
 * 设置内容区：云端规则同步、免打扰时段、电池优化、厂商保活引导与快捷磁贴。
 *
 * 所有系统设置跳转统一经 [KeepAliveNavigator]（`device/` 层唯一跳转出口），
 * UI 不直接拼装 `Intent`。
 *
 * 自 v3.2 起本内容**内嵌于「我的」页**（见 [com.ldp.adskip.ui.profile.ProfileScreen]），
 * 不再是独立一级页面：
 * - 底部导航从 5 项收敛为 4 项（首页 / 应用管理 / 跳过日志 / 我的）；
 * - 因此这里**不自带** `PageHeader` 与滚动容器——标题与滚动由宿主页负责，
 *   否则会出现「我的」页里嵌一个自带标题的滚动列，滑动时标题重复且滚动冲突。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    messenger: Messenger,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(messenger) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.ShowMessage -> messenger.show(effect.message)

                // 设置页不涉及关键词删除，KeywordRemoved 在此不会出现
                is UiEffect.KeywordRemoved -> Unit
            }
        }
    }
    LaunchedEffect(Unit) { viewModel.refreshBatteryStatus() }

    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard {
            SectionTitle(stringResource(R.string.settings_cloud_section))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.serverUrlInput,
                onValueChange = viewModel::onServerUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_server_label)) },
                placeholder = { Text(stringResource(R.string.settings_server_hint)) },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { viewModel.saveServerUrl(state.serverUrlInput) }) {
                    Text(stringResource(R.string.settings_save))
                }
                Button(onClick = viewModel::syncNow, enabled = !state.syncing) {
                    Text(
                        stringResource(
                            if (state.syncing) {
                                R.string.settings_syncing
                            } else {
                                R.string.settings_sync
                            },
                        ),
                    )
                }
            }
            state.syncResult?.let {
                Spacer(Modifier.height(12.dp))
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (state.lastSyncAt > 0L) {
                    stringResource(
                        R.string.settings_last_sync,
                        viewModel.formatLastSync(state.lastSyncAt),
                    )
                } else {
                    stringResource(R.string.settings_never_sync)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SectionHint(stringResource(R.string.settings_desc))
        }

        SectionCard {
            SectionTitle(stringResource(R.string.settings_schedule_section))
            // 统一走 components/LabeledSwitch：此前本页自带的 SwitchRow 只让开关本身
            // 可点，而 LabeledSwitch 整行可点（命中区域远大于开关）。同一个「设置开关」
            // 在应用列表里整行可点、在设置页只能点小开关，用户会当成其中一个是 bug。
            LabeledSwitch(
                title = stringResource(R.string.settings_auto_sync),
                checked = state.autoSync,
                onCheckedChange = viewModel::setAutoSync,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            LabeledSwitch(
                title = stringResource(R.string.settings_dnd),
                checked = state.dndEnabled,
                onCheckedChange = viewModel::setDndEnabled,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { pickingStart = true }, enabled = state.dndEnabled) {
                    Text(stringResource(R.string.settings_dnd_start))
                }
                FilledTonalButton(onClick = { pickingEnd = true }, enabled = state.dndEnabled) {
                    Text(stringResource(R.string.settings_dnd_end))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatMinuteRange(state.dndStartMinute, state.dndEndMinute),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            SectionTitle(stringResource(R.string.settings_keepalive_title))
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.settings_keepalive_vendor,
                    stringResource(vendorLabelRes(state.keepAliveVendor)),
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            SectionHint(stringResource(R.string.settings_keepalive_hint))
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::openKeepAliveSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_keepalive_open))
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { KeepAliveNavigator.openBatteryOptimizationSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.batteryExempt) {
                            R.string.settings_battery_done
                        } else {
                            R.string.settings_battery_allow
                        },
                    ),
                )
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(Modifier.padding(bottom = 16.dp))
            SectionHint(
                stringResource(
                    R.string.settings_tile_hint,
                    stringResource(R.string.tile_label),
                ),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::requestAddTile,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_tile_add))
            }
        }
    }

    if (pickingStart) {
        MinutePickerDialog(
            title = stringResource(R.string.settings_dnd_start),
            initialMinute = state.dndStartMinute,
            onDismiss = { pickingStart = false },
            onConfirm = { minute ->
                viewModel.setDndTimes(minute, state.dndEndMinute)
                pickingStart = false
            },
        )
    }
    if (pickingEnd) {
        MinutePickerDialog(
            title = stringResource(R.string.settings_dnd_end),
            initialMinute = state.dndEndMinute,
            onDismiss = { pickingEnd = false },
            onConfirm = { minute ->
                viewModel.setDndTimes(state.dndStartMinute, minute)
                pickingEnd = false
            },
        )
    }
}

/** M3 TimePicker 封装为对话框（分钟粒度，24 小时制） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinutePickerDialog(title: String, initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val pickerState = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) {
                Text(stringResource(R.string.btn_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
        text = { TimePicker(state = pickerState) },
    )
}

private fun formatMinuteRange(start: Int, end: Int): String = "%02d:%02d - %02d:%02d".format(
    Locale.getDefault(),
    start / 60,
    start % 60,
    end / 60,
    end % 60,
)
