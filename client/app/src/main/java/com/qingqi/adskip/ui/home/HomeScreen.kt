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
import com.qingqi.adskip.ui.theme.icons.AdSkipIcons
import kotlinx.coroutines.launch

/**
 * 首页 LazyColumn 的 item key 单一事实源。
 *
 * 「添加关键词」等入口要滚动到关键词卡：`animateScrollToItem` 只接受下标，
 * 旧实现把下标 4 硬编码在闭包里—— LazyColumn 上方增删任何一个 item 都会让它
 * 静默滚错位置（不报错、不崩溃，只是滚到别的东西），且没有任何测试能发现。
 *
 * 规则：item 顺序 = [order] 声明序；滚动目标一律经 [indexOf] 推导，不得出现
 * 魔法下标。新增/删除 item 时同步改这一处即可，配套 `HomeItemsTest` 锁住
 * 「源码中的 item 顺序 == order 声明序」。
 */
internal object HomeItems {
    const val HEADER = "home-header"
    const val STATUS = "home-status"
    const val QUICK_ACTIONS = "home-quick-actions"
    const val USAGE = "home-usage"
    const val KEYWORDS = "home-keywords"
    const val HINT = "home-hint"

    /** 组合顺序即声明序。 */
    val order: List<String> = listOf(HEADER, STATUS, QUICK_ACTIONS, USAGE, KEYWORDS, HINT)

    fun indexOf(key: String): Int = order.indexOf(key)
}

/**
 * 主页：服务状态、快捷操作、使用概览、关键词管理、模拟测试。
 *
 * 版式约定（v3.4 首页重设计，衔接 v3.2 的层级结论）：
 * - 页面自身**不**创建 `Scaffold`，inset 由外壳 `AdSkipShell` 统一分发；
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
        // 「添加关键词」快捷入口：滚到关键词卡让输入框进入视野。
        // 下标由 HomeItems.order 推导而非硬编码——增删 item 不会让这里静默失效。
        val scrollToKeywords: () -> Unit = {
            scope.launch { listState.animateScrollToItem(HomeItems.indexOf(HomeItems.KEYWORDS)) }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                // 浮层盖住首页时，把底层内容从语义树移除：浮层只吃触摸事件，被遮住的
                // 按钮仍会被 TalkBack 聚焦并激活，视障用户会点到一个看不见的东西。
                // 清除必须挂在**这层**而不能挂在浮层上——挂在浮层会连带清掉「跳过」
                // 按钮自身，引擎扫不到节点、自检必超时（2026-10-11 Mi 9 真机实测）。
                .then(
                    if (state.fakeAdVisible) Modifier.clearAndSetSemantics {} else Modifier,
                ),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                // 顶部留白由 PageHeader 自带：这里再留一次会比其他一级页多出一倍间距。
                top = 0.dp,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(key = HomeItems.HEADER) {
                // 一级页面都要有位置锚点（见 ScreenHeaderContractTest）。
                // 首页此前是四屏里唯一没有标题的：用户切过来后整屏像浮在空中的仪表盘。
                PageHeader(
                    title = stringResource(R.string.home_title),
                    subtitle = stringResource(R.string.home_subtitle),
                )
            }

            item(key = HomeItems.STATUS) {
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

            item(key = HomeItems.QUICK_ACTIONS) {
                QuickActionsRow(
                    onOpenLogs = onOpenLogs,
                    onOpenApps = onOpenApps,
                    onScrollToKeywords = scrollToKeywords,
                )
            }

            item(key = HomeItems.USAGE) { UsageTiles(state = state, lastAppLabel = lastAppLabel) }

            item(key = HomeItems.KEYWORDS) {
                KeywordsCard(
                    keywords = state.keywords,
                    onAdd = viewModel::addKeyword,
                    onRemoveAt = viewModel::removeKeyword,
                )
            }

            item(key = HomeItems.HINT) {
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
 * 绿调中性延续品牌色相，且深浅两套主题各自成立；不引入色值字面量。
 * 环仍用 56dp（`UiSizes.statusOrb`）横排在左——v3.2 压缩首屏高度的结论继续成立。
 */
@Composable
private fun StatusHero(running: Boolean, onPrimaryAction: () -> Unit, onTest: () -> Unit) {
    SectionCard(
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.xxl),
        brush = Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.surfaceContainerLowest,
                MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 参考新版视觉稿：服务状态是首页的第一视觉焦点，改为居中状态徽章，
            // 让用户打开首页后无需阅读多段说明即可判断服务是否生效。
            StatusOrb(
                running = running,
                diameter = UiSizes.statusOrbHero,
            )
            Spacer(Modifier.height(Spacing.md))
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
            Spacer(Modifier.height(Spacing.xl))

            // 主要操作保持高对比度；服务运行时改为次级强调，避免误导用户重复开启。
            if (running) {
                FilledTonalButton(
                    onClick = onPrimaryAction,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.btn_manage_settings))
                }
            } else {
                Button(
                    onClick = onPrimaryAction,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.btn_open_settings))
                }
            }

            Spacer(Modifier.height(Spacing.sm))
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
            icon = AdSkipIcons.Logs,
            label = stringResource(R.string.quick_open_logs),
            onClick = onOpenLogs,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            icon = AdSkipIcons.Plus,
            label = stringResource(R.string.quick_add_keyword),
            onClick = onScrollToKeywords,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            icon = AdSkipIcons.Apps,
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
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.md),
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
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionTitle(stringResource(R.string.stats_overview_title))
        SectionCard(contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
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
                    imageVector = AdSkipIcons.Plus,
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
                                imageVector = AdSkipIcons.Close,
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
 *
 * 语义树注意：本浮层**不能**自己挂 `clearAndSetSemantics`——那会把「跳过」按钮
 * 一起从语义树清掉，无障碍引擎与读屏都找不到它，自检必然超时（2026-10-11 真机
 * 实测复现）。隐藏底层首页内容的职责在 [HomeScreen] 调用处：浮层可见时对
 * LazyColumn 施清除，只藏下层、不伤本层。
 */
@Composable
private fun FakeAdOverlay(countdown: Int, onSkipClicked: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
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
