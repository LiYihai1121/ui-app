package com.qingqi.adskip.ui.apps

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingqi.adskip.R
import com.qingqi.adskip.ui.components.EmptyState
import com.qingqi.adskip.ui.components.ErrorState
import com.qingqi.adskip.ui.components.PageHeader
import com.qingqi.adskip.ui.components.RowDivider
import com.qingqi.adskip.ui.components.SearchField
import com.qingqi.adskip.ui.components.SectionCard
import com.qingqi.adskip.ui.components.SegmentedTabs
import com.qingqi.adskip.ui.components.SkeletonList
import com.qingqi.adskip.ui.theme.Spacing
import com.qingqi.adskip.ui.theme.UiSizes
import com.qingqi.adskip.ui.theme.icons.AdSkipIcons

/** 应用列表的三种视图（设计稿的「全部应用 / 已启用 / 未启用」）。 */
private enum class AppsFilter { ALL, ENABLED, DISABLED }

/**
 * 应用管理（v4 设计稿）。
 *
 * 页面结构：返回 + 标题 → 搜索 → 三段视图切换 → 应用列表（图标 + 名称 + 包名 + 开关）。
 *
 * 为什么从一级页降为二级页：v4 设计稿把底部导航的第二个位置给了「规则」（关键词管理），
 * 应用管理改由首页的「管理应用」瓦片进入。这不是单纯的排序变化——
 * 「规则」是配置的核心（决定跳过什么），「应用」是配置的例外（决定对谁生效），
 * 把核心提到一级、例外降到二级，与用户「先定规则、再挑例外」的实际顺序一致。
 *
 * 视图切换用**三态**而不是 v3 的「只看已启用」布尔开关：布尔只能表达两个视图，
 * 而「只看未启用」（= 找出还没生效的应用）是一个真实需求，布尔下无处可去。
 */
@Composable
fun AppsScreen(onBack: () -> Unit = {}, viewModel: AppsViewModel = viewModel(factory = AppsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(AppsFilter.ALL) }

    // 离开本页时清掉临时筛选态（理由见 AppsViewModel.clearTransientFilters）。
    DisposableEffect(Unit) {
        onDispose { viewModel.clearTransientFilters() }
    }

    val visible = remember(state.items, state.query, filter) {
        val q = state.query.trim().lowercase()
        state.items.filter { row ->
            val matchesFilter = when (filter) {
                AppsFilter.ALL -> true
                AppsFilter.ENABLED -> !row.disabled
                AppsFilter.DISABLED -> row.disabled
            }
            matchesFilter && (
                q.isEmpty() ||
                    row.label.lowercase().contains(q) ||
                    row.pkg.lowercase().contains(q)
                )
        }
    }
    val enabledCount = remember(state.items) { state.items.count { !it.disabled } }
    val disabledCount = remember(state.items) { state.items.count { it.disabled } }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.apps_title),
            onBack = onBack,
            backLabel = stringResource(R.string.action_back),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = stringResource(R.string.apps_search_hint),
                )
            }

            item {
                SegmentedTabs(
                    options = AppsFilter.entries,
                    selected = filter,
                    onSelect = { filter = it },
                    labelOf = { option ->
                        when (option) {
                            AppsFilter.ALL -> stringResource(R.string.apps_tab_all, state.items.size)
                            AppsFilter.ENABLED -> stringResource(R.string.apps_tab_enabled, enabledCount)
                            AppsFilter.DISABLED -> stringResource(R.string.apps_tab_disabled, disabledCount)
                        }
                    },
                )
            }

            when {
                state.loading -> item { SkeletonList(rows = 6) }

                state.failed -> item {
                    ErrorState(
                        title = stringResource(R.string.error_generic_title),
                        subtitle = stringResource(R.string.error_generic_hint),
                        icon = AdSkipIcons.Warning,
                        action = {
                            Button(onClick = viewModel::load) {
                                Text(stringResource(R.string.btn_retry))
                            }
                        },
                    )
                }

                visible.isEmpty() -> item {
                    EmptyState(
                        title = stringResource(R.string.apps_empty_title),
                        subtitle = stringResource(R.string.apps_hint),
                        icon = AdSkipIcons.Apps,
                    )
                }

                else -> item {
                    SectionCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
                        visible.forEachIndexed { index, row ->
                            AppToggleRow(
                                row = row,
                                onToggle = { enabled -> viewModel.setEnabled(row.pkg, enabled) },
                            )
                            if (index != visible.lastIndex) {
                                RowDivider(startIndent = Spacing.xxxl)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.apps_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
        }
    }
}

/**
 * 应用行：图标 + 名称 + 包名 + 开关。整行可点。
 *
 * 无障碍：整行是**一个** `Role.Switch` 节点（`toggleable`），读屏一次念完
 * 「微信，com.tencent.mm，已开启，开关」；`Switch` 自身置为不可交互，
 * 避免出现第二个可点区域。
 */
@Composable
private fun AppToggleRow(row: AppsViewModel.AppRow, onToggle: (Boolean) -> Unit) {
    val enabled = !row.disabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon = row.icon, label = row.label)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.pkg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

/**
 * 应用图标：有图标用图标，没有则回退为「首字 + 底衬」的占位。
 *
 * 回退不是为了好看——部分 ROM 的 `loadIcon` 会抛异常或返回 null（本项目在
 * 定制系统上实测到过），没有回退时整行会留一个空洞，看起来像渲染失败。
 */
@Composable
private fun AppIcon(icon: Drawable?, label: String) {
    val bitmap = remember(icon) { icon?.toImageBitmap() }
    Box(
        modifier = Modifier
            .size(UiSizes.appIcon)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(UiSizes.appIcon),
            )
        } else {
            Text(
                text = label.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 把 [Drawable] 渲染成固定边长的位图（不依赖 core-ktx 的 `toBitmap` 扩展）。 */
private fun Drawable.toImageBitmap(): ImageBitmap {
    val size = ICON_PIXELS
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    setBounds(0, 0, size, size)
    draw(canvas)
    return bitmap.asImageBitmap()
}

/** 图标位图边长（像素）。取 96 是为了在三倍屏上仍不糊，同时不至于占太多内存。 */
private const val ICON_PIXELS = 96
