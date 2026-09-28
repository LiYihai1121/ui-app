package com.ldp.adskip.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ldp.adskip.R
import com.ldp.adskip.ui.components.EmptyState
import com.ldp.adskip.ui.components.PageHeader
import com.ldp.adskip.ui.components.SkeletonList
import com.ldp.adskip.ui.components.TwoLineRow

/**
 * 应用管理：所有可启动应用，逐项开关自动跳过并显示跳过次数。
 *
 * 页面不再自带 `Scaffold` 与返回箭头——导航由外壳底部栏统一承载，
 * 本页只负责内容（含一个轻量标题，见 [PageHeader]）。
 *
 * UX 约定（组件库补齐批次）：
 * - 应用一多（实测模拟器 30+，真机常见 100+）逐个滚动找不现实，
 *   提供按应用名 / 包名的即时搜索，以及「只看已启用」筛选；
 * - 首屏加载用 [SkeletonList] 骨架替代转圈，提前传达列表布局；
 * - 行统一走 [TwoLineRow]，与日志页共享行高与省略策略；
 * - 搜索无结果与列表为空是两种语义，空态文案分开。
 */
@Composable
fun AppsScreen(viewModel: AppsViewModel = viewModel(factory = AppsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val visible = remember(state.items, state.query, state.onlyEnabled) { state.visibleItems }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.apps_title),
            subtitle = stringResource(R.string.apps_hint),
        )

        SearchField(
            query = state.query,
            onQueryChange = viewModel::setQuery,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        FilterChip(
            selected = state.onlyEnabled,
            onClick = { viewModel.setOnlyEnabled(!state.onlyEnabled) },
            label = { Text(stringResource(R.string.apps_filter_enabled)) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when {
            state.loading && state.items.isEmpty() -> SkeletonList(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            visible.isEmpty() -> EmptyState(
                icon = Icons.Default.Search,
                title = stringResource(
                    if (state.items.isEmpty()) R.string.apps_empty else R.string.apps_no_match,
                ),
                subtitle = stringResource(
                    if (state.items.isEmpty()) R.string.apps_loading else R.string.apps_no_match_hint,
                ),
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                items(visible, key = { it.pkg }) { row ->
                    AppRowItem(row = row, onToggle = viewModel::setEnabled)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** 应用搜索框：即时过滤，输入非空时提供一键清除。 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        placeholder = { Text(stringResource(R.string.apps_search_placeholder)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.apps_search_clear),
                    )
                }
            }
        },
    )
}

@Composable
private fun AppRowItem(row: AppsViewModel.AppRow, onToggle: (String, Boolean) -> Unit) {
    TwoLineRow(
        title = row.label,
        subtitle = subtitle(row),
        leading = {
            val bitmap = remember(row.icon) { row.icon?.toImageBitmap() }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Spacer(Modifier.size(40.dp))
            }
        },
        trailing = {
            Switch(
                checked = !row.disabled,
                onCheckedChange = { checked -> onToggle(row.pkg, checked) },
            )
        },
    )
}

@Composable
private fun subtitle(row: AppsViewModel.AppRow): String = when {
    row.disabled -> stringResource(R.string.apps_disabled)
    row.count > 0 -> stringResource(R.string.apps_count, row.count)
    else -> stringResource(R.string.apps_never)
}

/** Drawable 转 ImageBitmap（无 accompanist 依赖的轻量转换） */
private fun android.graphics.drawable.Drawable.toImageBitmap(): androidx.compose.ui.graphics.ImageBitmap {
    val size = 128
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    setBounds(0, 0, size, size)
    draw(canvas)
    return bitmap.asImageBitmap()
}
