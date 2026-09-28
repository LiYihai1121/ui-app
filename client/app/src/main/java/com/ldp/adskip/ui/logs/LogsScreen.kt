package com.ldp.adskip.ui.logs

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ldp.adskip.R
import com.ldp.adskip.ui.components.PageHeader

/**
 * 跳过日志：最近 200 条自动跳过记录，支持清空与分享。
 */
/**
 * 跳过日志：最近 200 条自动跳过记录，支持清空与分享。
 *
 * 清空 / 分享从顶栏迁到标题下方的操作行：顶栏已由外壳统一管理，
 * 把动作留在内容流里更符合「标题 → 动作 → 内容」的阅读顺序。
 */
@Composable
fun LogsScreen(
    viewModel: LogsViewModel = viewModel(factory = LogsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.reload() }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = stringResource(R.string.logs_title))

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    viewModel.clear()
                    Toast.makeText(context, R.string.logs_cleared, Toast.LENGTH_SHORT).show()
                },
                enabled = state.logs.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.logs_clear))
            }
            FilledTonalButton(
                onClick = {
                    val text = viewModel.shareText()
                    if (text == null) {
                        Toast.makeText(
                            context, R.string.logs_empty, Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_SUBJECT,
                                        context.getString(R.string.logs_title)
                                    )
                                    putExtra(Intent.EXTRA_TEXT, text)
                                },
                                context.getString(R.string.logs_share)
                            )
                        )
                    }
                },
                enabled = state.logs.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.logs_share))
            }
        }

        if (state.logs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.logs_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(state.logs, key = { "${it.ts}:${it.pkg}:${it.label}" }) { entry ->
                    Column(Modifier.padding(vertical = 10.dp)) {
                        Text(text = entry.label, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${viewModel.formatTimestamp(entry.ts)} ｜ ${entry.pkg}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

