package com.ldp.adskip.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ldp.adskip.R
import com.ldp.adskip.device.KeepAliveNavigator
import com.ldp.adskip.ui.UiEffect
import com.ldp.adskip.ui.components.SectionCard
import com.ldp.adskip.ui.components.SectionHint
import com.ldp.adskip.ui.components.SectionTitle
import com.ldp.adskip.ui.components.StatusOrb
import com.ldp.adskip.ui.components.rememberAppLabel
import com.ldp.adskip.ui.theme.StatusColors

/**
 * 主页：服务状态、跳过统计、关键词管理、模拟测试。
 *
 * 版式约定（v3.2 首页重设计）：
 * - 页面自身**不**创建 `Scaffold`，inset 由外壳 `AdskipShell` 统一分发；
 * - 根节点挂 `imePadding()`，软键盘弹出时关键词输入框不会被遮住；
 * - 状态语义（运行 / 停止）只用两种颜色表达，其余元素一律走中性色，
 *   避免旧版「绿 / 蓝 / 紫」三色各说各话。
 *
 * 信息层级（v3.2 重排的理由，直接来自 v3.1 的真机截图）：
 * 旧版把 108dp 状态环 + 巨号状态标题竖排在首屏顶部，**在 1080×2340 上要滑过一次
 * 才能看到关键词输入框**——而关键词是这个 app 唯一需要用户主动操作的东西。
 * 更糟的是「打开无障碍设置」与「测试」都是 filled 按钮，视觉权重相同，
 * 用户分不清哪个是「必须做」、哪个是「想验证」。
 *
 * 因此改为：
 * 1. 状态卡横向排布（环在左、文案在右），大幅压缩首屏高度，
 *    让关键词区成为首屏之下第一个完整可见的交互区；
 * 2. 主 / 次动作分级：服务未开启时主 CTA 用 filled，运行中降级为 tonal，
 *    「测试」全程用 `TextButton`——它是可选的验证动作，不该和「去开启服务」抢焦点；
 * 3. 统计从两张大数字卡降为一行摘要：它是佐证而非主角。
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lastAppLabel = rememberAppLabel(state.lastApp)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.ShowMessage ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StatusHero(
                    running = state.serviceRunning,
                    onPrimaryAction = {
                        if (!KeepAliveNavigator.openAccessibilitySettings(context)) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_open_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onTest = viewModel::startFakeAdTest
                )
            }

            item { StatsRow(state = state, lastAppLabel = lastAppLabel) }

            item {
                KeywordsCard(
                    keywords = state.keywords,
                    onAdd = viewModel::addKeyword,
                    onRemoveAt = viewModel::removeKeyword
                )
            }

            item {
                SectionHint(
                    text = stringResource(R.string.how_it_works),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = state.fakeAdVisible,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160))
        ) {
            FakeAdOverlay(
                countdown = state.countdown,
                onSkipClicked = viewModel::onManualSkipClicked
            )
        }
    }
}


/**
 * 状态主视觉：状态环 + 结论式文案 + 主行动按钮。
 *
 * 文案遵循「先结论后解释」：标题直接说服务是否生效，提示语再解释怎么修，
 * 避免旧版把说明和操作混在一句长文本里。
 *
 * v3.2 版式改动：环与文案由**竖排**改为**横排**（环 108dp → 56dp）。
 * 旧版在 1080×2340 上，状态卡就占掉首屏约 60% 高度，关键词输入框必须滑过一次
 * 才看得到——而关键词是这个 app 唯一需要用户主动操作的东西。横排后状态卡高度
 * 从约 300dp 降到约 150dp，关键词区成为首屏之下第一个完整可见的交互区。
 */
@Composable
private fun StatusHero(
    running: Boolean,
    onPrimaryAction: () -> Unit,
    onTest: () -> Unit
) {
    SectionCard(contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusOrb(
                running = running,
                contentDescription = stringResource(
                    if (running) R.string.status_on else R.string.status_off
                ),
                diameter = 56.dp
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (running) R.string.status_on else R.string.status_off),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (running) StatusColors.on else StatusColors.off
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        if (running) R.string.status_on_hint else R.string.status_off_hint
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 动作分级：只有服务没开时，「去开启」才是用户必须做的事，用 filled 表达；
        // 已运行时降级为 tonal，避免让用户误以为还有必须点的操作。
        if (running) {
            FilledTonalButton(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_open_settings))
            }
        } else {
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_open_settings))
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // 「测试」是可选的验证动作，用 TextButton 让它明确矮于主行动。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = onTest, enabled = running) {
                Text(stringResource(R.string.btn_test))
            }
        }
    }
}

/**
 * 跳过统计：一行摘要。
 *
 * v3.2 降级说明：旧版是两张 `headlineMedium` 大数字卡，和状态主视觉抢注意力。
 * 但统计的真正作用是「佐证服务在干活」，不是用户的主要目标——用户的目标是
 * 让广告别弹。所以累计跳过保留为主数字，最近应用降为次要说明并允许省略。
 *
 * 「最近应用」为空时整段不显示：空占位比不显示更糟，它会让用户以为数据丢了。
 */
@Composable
private fun StatsRow(
    state: HomeViewModel.UiState,
    lastAppLabel: String
) {
    SectionCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.stats_total_short, state.totalSkips),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (lastAppLabel.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.stats_recent_short, lastAppLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
private fun KeywordsCard(
    keywords: List<String>,
    onAdd: (String) -> Unit,
    onRemoveAt: (Int) -> Unit
) {
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
        Spacer(Modifier.height(4.dp))
        SectionHint(stringResource(R.string.keywords_hint))
        Spacer(Modifier.height(16.dp))

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
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )

        AnimatedVisibility(
            visible = keywords.isNotEmpty(),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180))
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                keywords.forEachIndexed { index, keyword ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveAt(index) },
                        label = { Text(keyword) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(
                                    R.string.keyword_remove_cd, keyword
                                ),
                                modifier = Modifier.size(InputChipDefaults.AvatarSize)
                            )
                        }
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
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.fake_ad_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.fake_ad_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(40.dp))
            Button(onClick = onSkipClicked) {
                Text(stringResource(R.string.fake_ad_skip))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.fake_ad_countdown, countdown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
