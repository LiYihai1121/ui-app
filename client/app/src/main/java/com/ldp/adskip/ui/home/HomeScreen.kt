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
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ldp.adskip.R
import com.ldp.adskip.device.KeepAliveNavigator
import com.ldp.adskip.ui.UiEffect
import com.ldp.adskip.ui.components.SectionCard
import com.ldp.adskip.ui.components.SectionHint
import com.ldp.adskip.ui.components.SectionTitle
import com.ldp.adskip.ui.components.StatTile
import com.ldp.adskip.ui.components.StatusOrb
import com.ldp.adskip.ui.components.rememberAppLabel
import com.ldp.adskip.ui.theme.StatusColors

/**
 * 主页：服务状态、跳过统计、关键词管理、模拟测试。
 *
 * 版式约定（v3.1 UI 重设计）：
 * - 页面自身**不**创建 `Scaffold`，inset 由外壳 `AdskipShell` 统一分发；
 * - 根节点挂 `imePadding()`，软键盘弹出时关键词输入框不会被遮住；
 * - 状态语义（运行 / 停止）只用两种颜色表达，其余元素一律走中性色，
 *   避免旧版「绿 / 蓝 / 紫」三色各说各话。
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
                    }
                )
            }

            item { StatsCard(state = state, lastAppLabel = lastAppLabel) }

            item {
                KeywordsCard(
                    keywords = state.keywords,
                    onAdd = viewModel::addKeyword,
                    onRemoveAt = viewModel::removeKeyword
                )
            }

            item {
                FilledTonalButton(
                    onClick = viewModel::startFakeAdTest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_test))
                }
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
 * 状态主视觉：自绘指示环 + 结论式文案 + 主行动按钮。
 *
 * 文案遵循「先结论后解释」：标题直接说服务是否生效，提示语再解释怎么修，
 * 避免旧版把说明和操作混在一句长文本里。
 */
@Composable
private fun StatusHero(
    running: Boolean,
    onPrimaryAction: () -> Unit
) {
    SectionCard(contentPadding = PaddingValues(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusOrb(
                running = running,
                contentDescription = stringResource(
                    if (running) R.string.status_on else R.string.status_off
                )
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(if (running) R.string.status_on else R.string.status_off),
                style = MaterialTheme.typography.headlineSmall,
                color = if (running) StatusColors.on else StatusColors.off
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    if (running) R.string.status_on_hint else R.string.status_off_hint
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_open_settings))
            }
        }
    }
}

/** 统计卡片：累计跳过 + 最近应用（应用名而非包名）。 */
@Composable
private fun StatsCard(
    state: HomeViewModel.UiState,
    lastAppLabel: String
) {
    SectionCard {
        SectionTitle(stringResource(R.string.stats_title))
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            StatTile(
                value = state.totalSkips.toString(),
                label = stringResource(R.string.stats_total_label),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = lastAppLabel,
                label = stringResource(R.string.stats_recent_label),
                modifier = Modifier.weight(1f)
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
