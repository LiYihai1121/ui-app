package com.ldp.adskip.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ldp.adskip.R
import java.util.Locale

/**
 * 云端规则同步设置：服务器地址、手动/自动同步、免打扰时段、电池优化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) { viewModel.refreshBatteryStatus() }

    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------- 服务器地址 ----------
            OutlinedTextField(
                value = state.serverUrlInput,
                onValueChange = viewModel::onServerUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_server_label)) },
                placeholder = { Text(stringResource(R.string.settings_server_hint)) },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { viewModel.saveServerUrl(state.serverUrlInput) }) {
                    Text(stringResource(R.string.settings_save))
                }
                Button(onClick = viewModel::syncNow, enabled = !state.syncing) {
                    Text(
                        stringResource(
                            if (state.syncing) R.string.settings_syncing else R.string.settings_sync
                        )
                    )
                }
            }
            state.syncResult?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = if (state.lastSyncAt > 0L) {
                    stringResource(
                        R.string.settings_last_sync,
                        viewModel.formatLastSync(state.lastSyncAt)
                    )
                } else {
                    stringResource(R.string.settings_never_sync)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.settings_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // ---------- 自动同步 ----------
            SwitchRow(
                title = stringResource(R.string.settings_auto_sync),
                checked = state.autoSync,
                onCheckedChange = viewModel::setAutoSync
            )

            HorizontalDivider()

            // ---------- 免打扰时段 ----------
            SwitchRow(
                title = stringResource(R.string.settings_dnd),
                checked = state.dndEnabled,
                onCheckedChange = viewModel::setDndEnabled
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { pickingStart = true }, enabled = state.dndEnabled) {
                    Text(stringResource(R.string.settings_dnd_start))
                }
                FilledTonalButton(onClick = { pickingEnd = true }, enabled = state.dndEnabled) {
                    Text(stringResource(R.string.settings_dnd_end))
                }
            }
            Text(
                text = formatMinuteRange(state.dndStartMinute, state.dndEndMinute),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // ---------- 电池优化 ----------
            Button(onClick = { openBatterySettings(context) }) {
                Text(
                    stringResource(
                        if (state.batteryExempt) {
                            R.string.settings_battery_done
                        } else {
                            R.string.settings_battery_allow
                        }
                    )
                )
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
            }
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
            }
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** M3 TimePicker 封装为对话框（分钟粒度，24 小时制） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinutePickerDialog(
    title: String,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = { TimePicker(state = pickerState) }
    )
}

private fun openBatterySettings(context: android.content.Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

private fun formatMinuteRange(start: Int, end: Int): String =
    "%02d:%02d - %02d:%02d".format(
        Locale.getDefault(), start / 60, start % 60, end / 60, end % 60
    )
