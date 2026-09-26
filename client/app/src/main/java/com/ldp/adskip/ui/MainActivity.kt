package com.ldp.adskip.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ldp.adskip.ui.apps.AppsScreen
import com.ldp.adskip.ui.home.HomeScreen
import com.ldp.adskip.ui.logs.LogsScreen
import com.ldp.adskip.ui.settings.SettingsScreen
import com.ldp.adskip.ui.theme.AdskipTheme

/**
 * 唯一 Activity：承载 Navigation Compose 导航的四个页面。
 *
 * 旧架构的四个 View 页面（MainActivity / AppListActivity / LogsActivity /
 * SettingsActivity）合并为本单 Activity + Compose 架构。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 自动同步 Job 的兜底重注册在组合根 AdskipApp.onCreate 中完成

        setContent {
            AdskipTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(onNavigate = { route -> navController.navigate(route) })
                    }
                    composable(Routes.APPS) {
                        AppsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.LOGS) {
                        LogsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
