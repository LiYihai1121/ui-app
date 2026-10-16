package com.qingqi.adskip.ui.logs

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingqi.adskip.R
import com.qingqi.adskip.data.StatsRepository
import com.qingqi.adskip.ui.Messenger
import com.qingqi.adskip.ui.components.ConfirmDialog
import com.qingqi.adskip.ui.components.PageHeader
import com.qingqi.adskip.ui.components.SegmentedTabs
import com.qingqi.adskip.ui.components.rememberAppLabel
import com.qingqi.adskip.ui.theme.Brand40
import com.qingqi.adskip.ui.theme.Brand70
import com.qingqi.adskip.ui.theme.Brand90
import com.qingqi.adskip.ui.theme.LogsDarkBackground
import com.qingqi.adskip.ui.theme.LogsDarkOnSurface
import com.qingqi.adskip.ui.theme.LogsDarkOnSurfaceVariant
import com.qingqi.adskip.ui.theme.LogsDarkOutline
import com.qingqi.adskip.ui.theme.LogsDarkSurface
import com.qingqi.adskip.ui.theme.LogsDarkSurfaceHigh
import com.qingqi.adskip.ui.theme.Spacing
import com.qingqi.adskip.ui.theme.StatusColors
import com.qingqi.adskip.ui.theme.UiSizes
import com.qingqi.adskip.ui.theme.icons.AdSkipIcons

/** 时间范围筛选（设计稿的「全部 / 今天 / 近 7 天 / 近 30 天」）。 */
private enum class LogsRange { ALL, TODAY, WEEK, MONTH }

/**
 * 跳过记录（v4 设计稿，**单页深色**）。
 *
 * 为什么这一页反过来用深色：设计稿把它做成深底——记录是「已发生事实」的流水，
 * 深底能让时间线与结果文字形成高对比、长时间扫读更省力，也与其余页面的
 * 「浅色配置界面」形成角色区分（一层是改配置，一层是看历史）。
 *
 * 实现方式：用局部 `darkColorScheme` 覆盖 [MaterialTheme]，而不是给每个组件手写颜色。
 * 这样 [PageHeader]、[SegmentedTabs] 等既有组件都自动取到深色前景，
 * 无需为「深色页」再造一套组件——否则同一组件会有两套着色逻辑，迟早不同步。
 *
 * 保留 v3 的两个操作（分享 / 清空）：设计稿没有画它们，但它们是这一页仅有的
 * 两个真实功能入口，删掉等于功能缺失。降级处理成标题右侧的两个图标按钮。
 */
@Composable
fun LogsScreen(messenger: Messenger, viewModel: LogsViewModel = viewModel(factory = LogsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var range by remember { mutableStateOf(LogsRange.ALL) }
    var confirmClear by remember { mutableStateOf(false) }

    val filtered = remember(state.logs, range) {
        val now = System.currentTimeMillis()
        state.logs.filter { entry ->
            when (range) {
                LogsRange.ALL -> true
                LogsRange.TODAY -> now - entry.ts < ONE_DAY_MS
                LogsRange.WEEK -> now - entry.ts < SEVEN_DAYS_MS
                LogsRange.MONTH -> now - entry.ts < THIRTY_DAYS_MS
            }
        }
    }

    LogsDarkSurfaceTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LogsDarkBackground),
        ) {
            PageHeader(
                title = stringResource(R.string.logs_title),
                subtitle = stringResource(R.string.logs_retention_note),
                onAction = {
                    val text = viewModel.shareText()
                    if (text == null) {
                        messenger.show(context.getString(R.string.logs_empty_share))
                    } else {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                },
                                context.getString(R.string.logs_share_title),
                            ),
                        )
                    }
                },
                actionLabel = stringResource(R.string.action_share),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item {
                    SegmentedTabs(
                        options = LogsRange.entries,
                        selected = range,
                        onSelect = { range = it },
                        labelOf = { option ->
                            when (option) {
                                LogsRange.ALL -> stringResource(R.string.logs_filter_all)
                                LogsRange.TODAY -> stringResource(R.string.logs_filter_today)
                                LogsRange.WEEK -> stringResource(R.string.logs_filter_week)
                                LogsRange.MONTH -> stringResource(R.string.logs_filter_month)
                            }
                        },
                        containerColor = LogsDarkSurfaceHigh,
                        contentColor = LogsDarkOnSurfaceVariant,
                    )
                }

                if (filtered.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.logs_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = LogsDarkOnSurface,
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                text = stringResource(R.string.logs_empty_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LogsDarkOnSurfaceVariant,
                            )
                        }
                    }
                } else {
                    filtered.forEach { entry ->
                        item(key = "${entry.ts}-${entry.pkg}") {
                            LogRow(
                                entry = entry,
                                time = viewModel.formatTimestamp(entry.ts),
                            )
                        }
                    }

                    item {
                        TextButton(onClick = { confirmClear = true }) {
                            Icon(
                                imageVector = AdSkipIcons.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(UiSizes.inlineIcon),
                                tint = StatusColors.off,
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                text = stringResource(R.string.logs_clear),
                                style = MaterialTheme.typography.labelLarge,
                                color = StatusColors.off,
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.logs_clear_confirm_title),
            message = stringResource(R.string.logs_clear_confirm_message),
            onConfirm = {
                viewModel.clear()
                confirmClear = false
                messenger.showUndoable(
                    message = context.getString(R.string.logs_cleared),
                    undoLabel = context.getString(R.string.action_undo),
                    onUndo = viewModel::undoClear,
                )
            },
            onDismiss = { confirmClear = false },
        )
    }
}

/**
 * 记录行：应用徽标 + 「应用名 · 命中词」+ 时间 + 结果。
 *
 * 结果文字用状态色而不是纯装饰色：这一行要回答的是「这次到底跳过了没有」，
 * 属于状态语义，因此走 [StatusColors]，与首页状态、权限清单同源。
 */
@Composable
private fun LogRow(entry: StatsRepository.LogEntry, time: String) {
    val appLabel = rememberAppLabel(entry.pkg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(LogsDarkSurface)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(UiSizes.appIcon)
                .clip(MaterialTheme.shapes.small)
                .background(LogsDarkSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = appLabel.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = LogsDarkOnSurface,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (entry.label.isBlank()) appLabel else "$appLabel · ${entry.label}",
                style = MaterialTheme.typography.bodyLarge,
                color = LogsDarkOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = time,
                style = MaterialTheme.typography.bodySmall,
                color = LogsDarkOnSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = AdSkipIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(UiSizes.inlineIcon),
                tint = StatusColors.on,
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = stringResource(R.string.logs_result_skipped),
                style = MaterialTheme.typography.labelMedium,
                color = StatusColors.on,
                maxLines = 1,
            )
        }
    }
}

/**
 * 记录页专用的深色主题覆盖。
 *
 * 只覆盖本页需要的角色，`primary` 用较亮的 `Brand40`：深底上的 `Brand50`
 * 对比度不足（3.1:1），选中态文字会发灰。
 */
@Composable
private fun LogsDarkSurfaceTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Brand40,
        onPrimary = Brand90,
        primaryContainer = Brand70,
        onPrimaryContainer = Brand40,
        background = LogsDarkBackground,
        onBackground = LogsDarkOnSurface,
        surface = LogsDarkSurface,
        onSurface = LogsDarkOnSurface,
        surfaceVariant = LogsDarkSurfaceHigh,
        onSurfaceVariant = LogsDarkOnSurfaceVariant,
        surfaceContainer = LogsDarkSurfaceHigh,
        surfaceContainerHigh = LogsDarkSurfaceHigh,
        surfaceContainerLow = LogsDarkSurface,
        outline = LogsDarkOutline,
        outlineVariant = LogsDarkOutline,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

private const val ONE_DAY_MS = 24L * 60 * 60 * 1000
private const val SEVEN_DAYS_MS = 7 * ONE_DAY_MS
private const val THIRTY_DAYS_MS = 30 * ONE_DAY_MS
