package com.qingqi.adskip.ui.settings

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingqi.adskip.R
import com.qingqi.adskip.device.KeepAliveNavigator
import com.qingqi.adskip.device.LanguageMode
import com.qingqi.adskip.ui.Messenger
import com.qingqi.adskip.ui.UiEffect
import com.qingqi.adskip.ui.components.LabeledChoiceRow
import com.qingqi.adskip.ui.components.LabeledSwitch
import com.qingqi.adskip.ui.components.SectionCard
import com.qingqi.adskip.ui.components.SectionHint
import com.qingqi.adskip.ui.components.SectionTitle
import com.qingqi.adskip.ui.theme.Spacing
import com.qingqi.adskip.ui.vendorLabelRes
import java.util.Locale

/**
 * 设置内容区：云端规则同步、免打扰时段、电池优化、厂商保活引导与快捷磁贴。
 *
 * 所有系统设置跳转统一经 [KeepAliveNavigator]（`device/` 层唯一跳转出口），
 * UI 不直接拼装 `Intent`。
 *
 * 自 v3.2 起本内容**内嵌于「我的」页**（见 [com.qingqi.adskip.ui.profile.ProfileScreen]），
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

                // 低版本切换语言需要重建 Activity 才能让新语言作用于资源解析。
                // ViewModel 不持有 Activity，故由界面侧执行这个生命周期操作。
                is UiEffect.RecreateActivity -> (context as? Activity)?.recreate()
            }
        }
    }
    // 电池豁免是系统侧状态：用户点按钮跳去系统设置允许后再返回，Activity 不重建，
    // 只在进屏读一次的 LaunchedEffect(Unit) 不会重跑，按钮会一直停在「允许后台运行」。
    // 因此每次都按 ON_RESUME 重查。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshBatteryStatus()
    }

    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var pickingLanguage by remember { mutableStateOf(false) }

    if (pickingLanguage) {
        LanguagePickerDialog(
            current = state.language,
            onPick = { mode ->
                pickingLanguage = false
                viewModel.setLanguage(mode)
            },
            onDismiss = { pickingLanguage = false },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        SectionCard {
            SectionTitle(stringResource(R.string.settings_language_section))
            Spacer(Modifier.height(Spacing.sm))
            LabeledChoiceRow(
                title = stringResource(R.string.settings_language_label),
                subtitle = stringResource(R.string.settings_language_hint),
                value = stringResource(state.language.labelRes()),
                onClick = { pickingLanguage = true },
            )
        }

        SectionCard {
            SectionTitle(stringResource(R.string.settings_cloud_section))
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = state.serverUrlInput,
                onValueChange = viewModel::onServerUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_server_label)) },
                placeholder = { Text(stringResource(R.string.settings_server_hint)) },
                singleLine = true,
            )
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                Spacer(Modifier.height(Spacing.md))
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Spacing.sm))
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
            Spacer(Modifier.height(Spacing.sm))
            if (state.isCleartextServer) {
                Text(
                    text = stringResource(R.string.settings_cleartext_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(Spacing.sm))
            }
            SectionHint(stringResource(R.string.settings_desc))
        }

        SectionCard {
            SectionTitle(stringResource(R.string.settings_security_section))
            Spacer(Modifier.height(Spacing.sm))
            SectionHint(stringResource(R.string.settings_security_desc))
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = state.signingKeyInput,
                onValueChange = viewModel::onSigningKeyChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_signing_key_label)) },
                placeholder = { Text(stringResource(R.string.settings_signing_key_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = state.certPinsInput,
                onValueChange = viewModel::onCertPinsChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_cert_pins_label)) },
                placeholder = { Text(stringResource(R.string.settings_cert_pins_hint)) },
                minLines = 2,
            )
            Spacer(Modifier.height(Spacing.md))
            FilledTonalButton(
                onClick = { viewModel.saveSecurityConfig(state.signingKeyInput, state.certPinsInput) },
            ) {
                Text(stringResource(R.string.settings_save_security))
            }
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
            HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
            LabeledSwitch(
                title = stringResource(R.string.settings_dnd),
                checked = state.dndEnabled,
                onCheckedChange = viewModel::setDndEnabled,
            )
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilledTonalButton(onClick = { pickingStart = true }, enabled = state.dndEnabled) {
                    Text(stringResource(R.string.settings_dnd_start))
                }
                FilledTonalButton(onClick = { pickingEnd = true }, enabled = state.dndEnabled) {
                    Text(stringResource(R.string.settings_dnd_end))
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = formatMinuteRange(state.dndStartMinute, state.dndEndMinute),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            SectionTitle(stringResource(R.string.settings_keepalive_title))
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(
                    R.string.settings_keepalive_vendor,
                    stringResource(vendorLabelRes(state.keepAliveVendor)),
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(Spacing.sm))
            SectionHint(stringResource(R.string.settings_keepalive_hint))
            Spacer(Modifier.height(Spacing.lg))
            Button(
                onClick = viewModel::openKeepAliveSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_keepalive_open))
            }
            Spacer(Modifier.height(Spacing.md))
            Button(
                onClick = { KeepAliveNavigator.openBatteryOptimizationSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.isBatteryExempt) {
                            R.string.settings_battery_done
                        } else {
                            R.string.settings_battery_allow
                        },
                    ),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            HorizontalDivider(Modifier.padding(bottom = Spacing.lg))
            SectionHint(
                stringResource(
                    R.string.settings_tile_hint,
                    stringResource(R.string.tile_label),
                ),
            )
            Spacer(Modifier.height(Spacing.lg))
            Button(
                onClick = viewModel::requestAddTile,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_tile_add))
            }
        }

        SectionCard {
            SectionTitle(stringResource(R.string.settings_snapshot_section))
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.settings_snapshot_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            FilledTonalButton(
                onClick = viewModel::exportNodeSnapshot,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_snapshot_export))
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

/**
 * 界面语言选择对话框。
 *
 * 用单选而非下拉/开关：只有三个取值，一次全列出来比「点开再选」少一步，
 * 而且用户能直接看到还有哪些可选——语言这种改错了会看不懂界面的设置，
 * 尤其需要「一眼看到能改回去」。
 */
@Composable
private fun LanguagePickerDialog(current: LanguageMode, onPick: (LanguageMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language_label)) },
        text = {
            Column {
                LanguageMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = mode == current,
                                role = Role.RadioButton,
                                onClick = { onPick(mode) },
                            )
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == current, onClick = null)
                        Spacer(Modifier.width(Spacing.md))
                        Text(stringResource(mode.labelRes()))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}

/**
 * 语言模式 → 显示名。
 *
 * 语言名**不随当前界面语言翻译**（英文环境下也显示「简体中文」）：
 * 用户看不懂当前语言时，正是靠母语名认路；把「简体中文」译成
 * "Simplified Chinese" 会让中文用户找不到自己的语言。
 */
@StringRes
private fun LanguageMode.labelRes(): Int = when (this) {
    LanguageMode.SYSTEM -> R.string.language_system
    LanguageMode.CHINESE -> R.string.language_zh
    LanguageMode.ENGLISH -> R.string.language_en
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
