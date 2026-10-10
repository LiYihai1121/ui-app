package com.qingqi.adskip.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
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
import com.qingqi.adskip.ui.components.PageHeader
import com.qingqi.adskip.ui.components.SectionCard
import com.qingqi.adskip.ui.components.SectionHint
import com.qingqi.adskip.ui.components.SectionTitle
import com.qingqi.adskip.ui.components.StatTile
import com.qingqi.adskip.ui.components.StatusOrb
import com.qingqi.adskip.ui.components.rememberAppLabel
import com.qingqi.adskip.ui.theme.Spacing
import com.qingqi.adskip.ui.theme.StatusColors
import com.qingqi.adskip.ui.theme.UiSizes
import kotlinx.coroutines.launch

/**
 * 主页：服务状态、快捷操作、使用概览、关键词管理、模拟测试。
 *
 * 版式约定（v3.4 首页重设计，衔接 v3.2 的层级结论）：
 * - 页面自身**不**创建 `Scaffold`，inset 由外壳 `AdskipShell` 统一分发；
 * - 根节点挂 `imePadding()`，软键盘弹出时关键词输入框不会被遮住；
 * - 状态语义（运行 / 停止）只用两种颜色表达，其余元素一律走中性色；
 * - 快捷操作行收录三个最高频动作（查看记录 / 添加关键词 / 管理应用），
 *   跳转走外壳传入的回调，不持有 NavController——页面保持纯展示；
 * - 使用概览从单行摘要升级为三格瓦片（累计跳过 / 关键词数 / 最近应用），
 *   全部来自既有 [HomeViewModel.UiState]，**不扩接口**。
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    messenger: Messenger,
    onOpenLogs: () -> Unit = {},
    onOpenApps: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lastAppLabel = rememberAppLabel(state.lastApp)

    // 每次回到前台都重查无障碍状态：用户从本页跳去系统设置改完开关再返回时，
    // Activity 并未重建（LaunchedEffect(Unit) 不会重跑），而 Service 的 onDestroy
    // 回调可能还没到——只靠进程信号会把过期的「运行中」一直显示下去，
    // 与读系统真值的快捷磁贴说法不一致。
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

                // 本页不提供语言切换（在设置里），该 Effect 不会从这里发出。
                // 显式列出而不加 `else`：将来新增 Effect 时编译器会提醒这里也要处理，
                // 而不是被 else 静默吞掉。
                is UiEffect.RecreateActivity -> Unit
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        // 「添加关键词」快捷入口：滚到关键词卡让输入框进入视野（item 4，0 起数）。
        val scrollToKeywords: () -> Unit = {
            scope.launch { listState.animateScrollToItem(index = 4) }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                // 顶部留白由 PageHeader 自带：这里再留一次会比其他一级页多出一倍间距。
                top = 0.dp,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                // 一级页面都要有位置锚点（见 ScreenHeaderContractTest）。
                // 首页此前是四屏里唯一没有标题的：用户切过来后整屏像浮在空中的仪表盘。
                PageHeader(title = stringResource(R.string.home_title))
            }

            item {
                StatusHero(
                    running = state.serviceRunning,
                    onPrimaryAction = {
                        if (!KeepAliveNavigator.openAccessibilitySettings(context)) {
                            messenger.show(context.getString(R.string.settings_open_failed))
                        }
                    },
                    onTest = viewModel::startFakeAdTest,
                )
            }

            item {
                QuickActionsRow(
                    onOpenLogs = onOpenLogs,
                    onOpenApps = onOpenApps,
                    onScrollToKeywords = scrollToKeywords,
                )
            }

            item { UsageTiles(state = state, lastAppLabel = lastAppLabel) }

            item {
                KeywordsCard(
                    keywords = state.keywords,
                    onAdd = viewModel::addKeyword,
                    onRemoveAt = viewModel::removeKeyword,
                )
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
 * 状态主视觉：渐变卡 + 状态环 + 结论式文案 + 主行动按钮。
 *
 * 文案遵循「先结论后解释」：标题直接说服务是否生效，提示语再解释怎么修，
 * 避免旧版把说明和操作混在一句长文本里。
 *
 * 渐变底只取主题角色色（primaryContainer → surfaceContainerLow）：
 * 蓝调中性延续品牌色相，且深浅两套主题各自成立；不引入色值字面量。
 * 环仍用 56dp（`UiSizes.statusOrb`）横排在左——v3.2 压缩首屏高度的结论继续成立。
 */
@Composable
private fun StatusHero(running: Boolean, onPrimaryAction: () -> Unit, onTest: () -> Unit) {
    SectionCard(
        contentPadding = PaddingValues(Spacing.lg),
        brush = Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusOrb(
                running = running,
                diameter = UiSizes.statusOrb,
            )
            Spacer(Modifier.width(Spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (running) R.string.status_on else R.string.status_off),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (running) StatusColors.on else StatusColors.off,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(
                        if (running) R.string.status_on_hint else R.string.status_off_hint,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        // 动作分级：只有服务没开时，「去开启」才是用户必须做的事，用 filled 表达；
        // 已运行时降级为 tonal，避免让用户误以为还有必须点的操作。
        if (running) {
            FilledTonalButton(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.btn_open_settings))
            }
        } else {
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.btn_open_settings))
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.xs),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        // 「测试」是可选的验证动作，用 TextButton 让它明确矮于主行动。
        //
        // 这里**不能**用 `enabled = running` 把按钮禁用：那样服务未开启时用户只看到一个
        // 灰按钮，既没有原因也没有下一步（灰控件不解释自己是常见 UX 反模式）。
        // HomeViewModel.startFakeAdTest() 已实现「未开启则提示先开启服务」的分支，
        // 之前被 enabled 挡住而永远不可达；改为始终可点后，那条提示才真正生效。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = onTest) {
                Text(stringResource(R.string.btn_test))
            }
        }
    }
}

/**
 * 快捷操作行：三个最高频动作一行直达。
 *
 * 跳转通过外壳传入的回调完成，本页不持有 NavController——保持「页面纯展示」
 * 的既有分层（insets、导航都归外壳管）。无障碍：每个入口是单个
 * `Role.Button` 语义节点（图标为装饰、`contentDescription = null`，
 * 文案由 label 承担），读屏一次读完「查看记录，按钮」。
 */
@Composable
private fun QuickActionsRow(onOpenLogs: () -> Unit, onOpenApps: () -> Unit, onScrollToKeywords: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        QuickAction(
            icon = Icons.Filled.DateRange,
            label = stringResource(R.string.quick_open_logs),
            onClick = onOpenLogs,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            icon = Icons.Filled.Add,
            label = stringResource(R.string.quick_add_keyword),
            onClick = onScrollToKeywords,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            icon = Icons.AutoMirrored.Filled.List,
            label = stringResource(R.string.quick_open_apps),
            onClick = onOpenApps,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(UiSizes.listIcon)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
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
 * 使用概览：三格瓦片（累计跳过 / 关键词数 / 最近应用）。
 *
 * 全部取自既有 [HomeViewModel.UiState]，不新增数据源。复用「我的」页的
 * [StatTile]，保证同一数字在全 app 只有一种视觉权重（v3.2 的教训）。
 * 「最近应用」为空时显示占位破折号而不是隐藏整格：三格布局保持稳定，
 * 空占位在本组件语义下表达的是「还没有记录」，与首页整行隐藏的历史结论不同。
 */
@Composable
private fun UsageTiles(state: HomeViewModel.UiState, lastAppLabel: String) {
    SectionCard(contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.lg)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            StatTile(
                value = state.totalSkips.toString(),
                label = stringResource(R.string.stats_total_label),
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

/**
 * 关键词卡片：输入 + 以 Chip 形式平铺已有关键词。
 *
 * 旧版把关键词渲染成整行 Text（`%1$s　✕`），删除区域只有文字本身那一小块；
 * 改为 InputChip 后，删除按钮有 48dp 的稳定命中区域，删除语义也由图标自解释。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordsCard(keywords: List<String>, onAdd: (String) -> Unit, onRemoveAt: (Int) -> Unit) {
    var input by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit() {
        if (input.isNotBlank()) {
            onAdd(input)
            input = ""
            keyboard?.hide()
        }
    }

    SectionCard {
        SectionTitle(stringResource(R.string.keywords_title))
        Spacer(Modifier.height(Spacing.xs))
        SectionHint(stringResource(R.string.keywords_hint))
        Spacer(Modifier.height(Spacing.lg))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            placeholder = { Text(stringResource(R.string.keyword_input_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.btn_add),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )

        AnimatedVisibility(
            visible = keywords.isNotEmpty(),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                keywords.forEachIndexed { index, keyword ->
                    val removeLabel = stringResource(R.string.keyword_remove_cd, keyword)
                    InputChip(
                        selected = false,
                        onClick = { onRemoveAt(index) },
                        label = { Text(keyword) },
                        // 点 chip 本体即删除，因此把「删除」声明成一个自定义语义动作，
                        // 读屏会念「删除关键词 跳过，按钮」而不是只报 chip 文本。
                        modifier = Modifier.semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(removeLabel) {
                                    onRemoveAt(index)
                                    true
                                },
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                // 动作语义已挂在 chip 上，图标本身不再重复声明。
                                contentDescription = null,
                                modifier = Modifier.size(InputChipDefaults.AvatarSize),
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * 模拟开屏广告浮层：服务生效时应被自动点击「跳过」。
 *
 * 覆盖在首页之上并吃掉触摸事件，防止误触穿透到底层界面。
 */
@Composable
private fun FakeAdOverlay(countdown: Int, onSkipClicked: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            // 浮层盖住首页时必须同时**从语义树上移除底层内容**：它只吃掉了触摸事件，
            // 被遮住的按钮仍可被 TalkBack 聚焦并激活，视障用户会点到一个看不见的东西。
            .clearAndSetSemantics {},
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.fake_ad_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.fake_ad_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xxxl))
            Button(onClick = onSkipClicked) {
                Text(stringResource(R.string.fake_ad_skip))
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = pluralStringResource(R.plurals.fake_ad_countdown, countdown, countdown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
