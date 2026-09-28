package com.ldp.adskip.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ldp.adskip.R
import com.ldp.adskip.ui.apps.AppsScreen
import com.ldp.adskip.ui.home.HomeScreen
import com.ldp.adskip.ui.logs.LogsScreen
import com.ldp.adskip.ui.profile.ProfileScreen
import com.ldp.adskip.ui.theme.AdskipTheme

/**
 * 唯一 Activity：承载 Navigation Compose 导航的四个页面。
 *
 * 旧架构的四个 View 页面（MainActivity / AppListActivity / LogsActivity /
 * SettingsActivity）合并为本单 Activity + Compose 架构。
 *
 * 边到边（edge-to-edge）约定：
 * `targetSdk = 35` 在 Android 15 上强制边到边，内容必然绘制到状态栏/导航栏之下。
 * 因此 insets 的唯一收口点是 [AdskipApp] 的 `Scaffold`：页面自身不再各自处理
 * `statusBarsPadding`（旧版只有 Home 漏了，导致标题压在时钟上）。
 * 页面内容统一只消费 `innerPadding`。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 自动同步 Job 的兜底重注册在组合根 AdskipApp.onCreate 中完成

        // 显式开启边到边，不依赖 targetSdk 的隐式行为（隐式行为随系统版本变化，过于脆弱）
        enableEdgeToEdge()

        // Android 10+ 默认会给三键导航栏加一层不透明遮罩，在边到边下表现为
        // 底部一条突兀的白带，与应用背景割裂。关闭对比度强制，让导航栏与应用背景连成一体。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            AdskipTheme {
                AdskipShell()
            }
        }
    }
}

/** 一级目的地：底部导航栏的唯一数据源。 */
private data class TopLevelDestination(val route: String, val labelRes: Int, val icon: ImageVector)

private val TopLevelDestinations = listOf(
    TopLevelDestination(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    TopLevelDestination(Routes.APPS, R.string.nav_apps, Icons.AutoMirrored.Filled.List),
    TopLevelDestination(Routes.LOGS, R.string.nav_logs, Icons.Filled.DateRange),
    TopLevelDestination(Routes.PROFILE, R.string.nav_profile, Icons.Filled.Person),
)

/**
 * 应用外壳：底部导航栏 + 导航容器。
 *
 * 把 `Scaffold` 提到这里之后，四个页面都不再自带 `Scaffold`，
 * inset（状态栏 / 导航栏 / 输入法）由本层一次性分发，页面只管内容。
 */
@Composable
private fun AdskipShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    // 系统返回键：在非首页时回到首页，而不是逐级退出应用
    BackHandler(enabled = currentRoute != null && currentRoute != Routes.HOME) {
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = true }
            launchSingleTop = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                TopLevelDestinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(destination.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.labelRes),
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(destination.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            enterTransition = {
                slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(220))
            },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = {
                slideOutHorizontally(tween(240)) { it / 6 } + fadeOut(tween(200))
            },
        ) {
            composable(Routes.HOME) { HomeScreen() }
            composable(Routes.APPS) { AppsScreen() }
            composable(Routes.LOGS) { LogsScreen() }
            composable(Routes.PROFILE) { ProfileScreen() }
        }
    }
}
