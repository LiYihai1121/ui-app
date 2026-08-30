package com.ldp.adskip.ui.home

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ldp.adskip.R
import com.ldp.adskip.ui.Routes
import com.ldp.adskip.ui.theme.StatusOff
import com.ldp.adskip.ui.theme.StatusOn

/**
 * 主页：服务状态、跳过统计、关键词管理、功能导航、模拟测试。
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            if (msg is HomeMessage.Text) {
                Toast.makeText(context, msg.value, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StatusCard(
                    running = state.serviceRunning,
                    onOpenSettings = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_open_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
            item { StatsCard(total = state.totalSkips, lastApp = state.lastApp) }
            item {
                KeywordsCard(
                    keywords = state.keywords,
                    onAdd = viewModel::addKeyword,
                    onRemoveAt = viewModel::removeKeyword
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onNavigate(Routes.APPS) }) {
                        Text(stringResource(R.string.nav_apps))
                    }
                    FilledTonalButton(onClick = { onNavigate(Routes.LOGS) }) {
                        Text(stringResource(R.string.nav_logs))
                    }
                    FilledTonalButton(onClick = { onNavigate(Routes.SETTINGS) }) {
                        Text(stringResource(R.string.nav_settings))
                    }
                }
            }
            item {
                Button(
                    onClick = viewModel::startFakeAdTest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_test))
                }
            }
            item {
                Text(
                    text = stringResource(R.string.how_it_works),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.fakeAdVisible) {
            FakeAdOverlay(
                countdown = state.countdown,
                onSkipClicked = viewModel::onManualSkipClicked
            )
        }
    }
}

@Composable
private fun StatusCard(running: Boolean, onOpenSettings: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(if (running) R.string.status_on else R.string.status_off),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (running) StatusOn else StatusOff
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    if (running) R.string.status_on_hint else R.string.status_off_hint
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.btn_open_settings))
            }
        }
    }
}

@Composable
private fun StatsCard(total: Int, lastApp: String) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            val lastDisplay = lastApp.ifBlank { stringResource(R.string.stats_none) }
            Text(
                text = stringResource(R.string.stats_format, total, lastDisplay),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun KeywordsCard(
    keywords: List<String>,
    onAdd: (String) -> Unit,
    onRemoveAt: (Int) -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.keywords_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.keywords_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            KeywordInput(onAdd = onAdd)

            if (keywords.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                keywords.forEachIndexed { index, kw ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRemoveAt(index) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.keyword_item, kw),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordInput(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.keyword_input_hint)) }
        )
        Spacer(Modifier.size(8.dp))
        IconButton(
            onClick = {
                onAdd(text)
                text = ""
            },
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.btn_add),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** 模拟开屏广告浮层：服务生效时应被自动点击「跳过」。 */
@Composable
private fun FakeAdOverlay(countdown: Int, onSkipClicked: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { /* 拦截触摸，防止误触穿透 */ },
        color = Color.White
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
                color = Color(0xFF1F2937),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.fake_ad_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B7280)
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onSkipClicked) {
                Text(stringResource(R.string.fake_ad_skip))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.fake_ad_countdown, countdown),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9CA3AF)
            )
        }
    }
}
