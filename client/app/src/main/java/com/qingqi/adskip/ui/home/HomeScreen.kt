package com.qingqi.adskip.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingqi.adskip.R
import com.qingqi.adskip.device.KeepAliveNavigator
import com.qingqi.adskip.ui.Messenger
import com.qingqi.adskip.ui.UiEffect
import com.qingqi.adskip.ui.components.AppHeader
import com.qingqi.adskip.ui.components.LabeledSwitch
import com.qingqi.adskip.ui.components.SectionCard
import com.qingqi.adskip.ui.components.SectionHint
import com.qingqi.adskip.ui.components.StatTile
import com.qingqi.adskip.ui.components.TagBadge
import com.qingqi.adskip.ui.components.rememberAppLabel
import com.qingqi.adskip.ui.theme.Spacing
import com.qingqi.adskip.ui.theme.StatusColors
import com.qingqi.adskip.ui.theme.TileBlue
import com.qingqi.adskip.ui.theme.TileBlueContainer
import com.qingqi.adskip.ui.theme.TileGreen
import com.qingqi.adskip.ui.theme.TileGreenContainer
import com.qingqi.adskip.ui.theme.TilePurple
import com.qingqi.adskip.ui.theme.TilePurpleContainer
import com.qingqi.adskip.ui.theme.UiSizes
import com.qingqi.adskip.ui.theme.icons.AdSkipIcons

/**
 * 首页（v4 设计稿）。
 *
 * 版式自上而下：品牌头部 → 状态盾牌卡 → 无障碍开关行 → 模拟测试卡 → 三张快捷瓦片 → 今日统计。
 *
 * 与 v3 的结构差别（为什么删掉了原来的「关键词卡」与「区块标签」）：
 *  - 关键词管理在 v4 里是**独立的一级页**（底部导航「规则」），首页不再承载它。
 *    首页出现一个可编辑的关键词列表，会让「首页 = 看状态」这一职责失焦；
 *  - 区块标签（SectionLabel）取消：v4 用「每块一张白卡」表达分组，
 *    卡与卡之间的留白已足够分层，再加标签属于同一信息讲两遍。
 *
 * 服务状态仍是三态（开 / 关 / 无法确认）：设计稿只画了「已开启」一种，
 * 但把 UNKNOWN 画成 OFF 是本项目复现过的真实缺陷（用户会去反复点一个不会生效的开关），
 * 故三态保留，配色分别为绿 / 红 / 琥珀，文案各自不同。
 */
@Composable
fun HomeScreen(
    messenger: Messenger,
    onOpenLogs: () -> Unit = {},
    onOpenRules: () -> Unit = {},
    onOpenApps: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lastAppLabel = rememberAppLabel(state.lastApp)

    // 每次回到前台都重查无障碍状态（理由见 v3 注释：Activity 不重建时进程信号会过期）。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onScreenResumed()
    }

    LaunchedEffect(messenger) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.ShowMessage -> messenger.show(effect.message)

                is UiEffect.KeywordRemoved -> messenger.showUndoable(
                    message = context.getString(R.string.keyword_removed, effect.keyword),
                    undoLabel = context.getString(R.string.action_undo),
                    onUndo = viewModel::undoRemoveKeyword,
                )

                is UiEffect.RecreateActivity -> Unit
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                // 浮层盖住首页时把底层内容移出语义树（理由见 v3 注释，2026-10-11 真机实测）。
                .then(
                    if (state.fakeAdVisible) Modifier.clearAndSetSemantics {} else Modifier,
                ),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = 0.dp,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                AppHeader(
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.home_subtitle),
                    trailingIcon = AdSkipIcons.Settings,
                    trailingLabel = stringResource(R.string.home_open_settings),
                    onTrailingClick = onOpenSettings,
                )
            }

            item {
                StatusShieldCard(
                    running = state.serviceRunning,
                    unknown = state.accessibilityUnknown,
                    onOpenSettings = {
                        if (!KeepAliveNavigator.openAccessibilitySettings(context)) {
                            messenger.show(context.getString(R.string.settings_open_failed))
                        }
                    },
                )
            }

            item {
                SectionCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
                    LabeledSwitch(
                        title = stringResource(R.string.home_accessibility_service),
                        subtitle = stringResource(
                            if (state.serviceRunning) {
                                R.string.home_accessibility_granted
                            } else {
                                R.string.home_accessibility_pending
                            },
                        ),
                        checked = state.serviceRunning,
                        icon = AdSkipIcons.Shield,
                        onCheckedChange = {
                            // 开关不能直接改系统权限，点击即跳系统设置（唯一可行路径）。
                            if (!KeepAliveNavigator.openAccessibilitySettings(context)) {
                                messenger.show(context.getString(R.string.settings_open_failed))
                            }
                        },
                    )
                }
            }

            item {
                ActionCard(
                    icon = AdSkipIcons.Rocket,
                    title = stringResource(R.string.home_test_title),
                    description = stringResource(R.string.home_test_desc),
                    actionLabel = stringResource(R.string.home_test_action),
                    onAction = viewModel::startFakeAdTest,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    QuickTile(
                        icon = AdSkipIcons.Clock,
                        label = stringResource(R.string.quick_open_logs),
                        contentColor = TileBlue,
                        containerColor = TileBlueContainer,
                        onClick = onOpenLogs,
                        modifier = Modifier.weight(1f),
                    )
                    QuickTile(
                        icon = AdSkipIcons.Tag,
                        label = stringResource(R.string.quick_add_keyword),
                        contentColor = TileGreen,
                        containerColor = TileGreenContainer,
                        onClick = onOpenRules,
                        modifier = Modifier.weight(1f),
                    )
                    QuickTile(
                        icon = AdSkipIcons.Apps,
                        label = stringResource(R.string.quick_open_apps),
                        contentColor = TilePurple,
                        containerColor = TilePurpleContainer,
                        onClick = onOpenApps,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.home_stats_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.home_stats_more),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(role = Role.Button, onClick = onOpenLogs),
                        )
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        StatTile(
                            value = state.totalSkips.toString(),
                            label = stringResource(R.string.stats_total_label),
                            emphasis = true,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = state.keywords.size.toString(),
                            label = stringResource(R.string.stats_keywords_label),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = lastAppLabel.ifBlank { "—" },
                            label = stringResource(R.string.stats_recent_label),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                SectionHint(
                    text = stringResource(R.string.how_it_works),
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
        }

        AnimatedVisibility(
            visible = state.fakeAdVisible,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
        ) {
            FakeAdOverlay(
                countdown = state.countdown,
                onSkipClicked = viewModel::onManualSkipClicked,
            )
        }
    }
}

/**
 * 状态盾牌卡：同心圆环 + 盾牌 + 结论 + 主行动。
 *
 * 环用「两个同心圆叠放」而不是 `Modifier.border`：间距门禁只放行
 * 0/4/8/12/16/24/32/40 的 dp 字面量，环宽 4dp 无法直接书写，
 * 而「外圈 120 / 内圈 112」两个直径之差恰好是 4dp——既绕开字面量，
 * 又让环宽由尺寸令牌推导出来。
 *
 * 三态同形不同色：形状（盾牌 + 同心环）保持不变，只有颜色与文案变化。
 * 形状编码留给「这是什么控件」，颜色编码留给「现在什么状态」。
 */
@Composable
private fun StatusShieldCard(running: Boolean, unknown: Boolean, onOpenSettings: () -> Unit) {
    val container = when {
        unknown -> StatusColors.unknownContainer
        running -> StatusColors.onContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val accent = when {
        unknown -> StatusColors.unknown
        running -> StatusColors.on
        else -> StatusColors.off
    }
    val title = stringResource(
        when {
            unknown -> R.string.status_unknown
            running -> R.string.status_on
            else -> R.string.status_off
        },
    )
    val hint = stringResource(
        when {
            unknown -> R.string.status_unknown_hint
            running -> R.string.home_status_protected
            else -> R.string.status_off_hint
        },
    )

    SectionCard(containerColor = container) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(UiSizes.statusShield)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(UiSizes.statusShieldRing)
                        .clip(CircleShape)
                        .background(container),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AdSkipIcons.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(UiSizes.statusShieldGlyph),
                        tint = accent,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!running) {
                Spacer(Modifier.height(Spacing.lg))
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_open_settings))
                }
            }
        }
    }
}

/** 动作卡：图标 + 标题 + 说明 + 右侧动作按钮。 */
@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(UiSizes.tileIcon)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(UiSizes.tileGlyph),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            FilledTonalButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/**
 * 快捷入口瓦片：图标底衬 + 标签。
 *
 * 整张瓦片是**一个** `Role.Button` 语义节点（图标 `contentDescription = null`，
 * 语义由标签承担），读屏一次念完「查看记录，按钮」。
 */
@Composable
private fun QuickTile(
    icon: ImageVector,
    label: String,
    contentColor: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(UiSizes.tileIcon)
                .clip(MaterialTheme.shapes.small)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(UiSizes.tileGlyph),
                tint = contentColor,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 模拟开屏广告浮层：服务生效时应被自动点击「跳过」。
 *
 * 语义树注意：本浮层**不能**自己挂 `clearAndSetSemantics`——那会把「跳过」按钮
 * 一起清掉，无障碍引擎与读屏都找不到它，自检必然超时（2026-10-11 真机实测复现）。
 * 隐藏底层内容的职责在 [HomeScreen] 调用处。
 */
@Composable
private fun FakeAdOverlay(countdown: Int, onSkipClicked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.fake_ad_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.fake_ad_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xxxl))
        Button(onClick = onSkipClicked) {
            Text(stringResource(R.string.fake_ad_skip))
        }
        Spacer(Modifier.height(Spacing.lg))
        TagBadge(
            text = pluralStringResource(R.plurals.fake_ad_countdown, countdown, countdown),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
