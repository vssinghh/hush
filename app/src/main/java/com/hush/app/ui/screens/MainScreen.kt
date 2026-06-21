package com.hush.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.hush.app.ui.navigation.BottomTabRoute
import com.hush.app.ui.screens.chat.ChatScreen
import com.hush.app.ui.screens.history.HistoryScreen
import com.hush.app.ui.screens.rules.RulesScreen
import com.hush.app.ui.screens.settings.SettingsScreen

@Composable
fun MainScreen(
    onResetOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val childNavController = rememberNavController()
    val tabs = listOf(
        BottomTabRoute.Chat,
        BottomTabRoute.Rules,
        BottomTabRoute.History,
        BottomTabRoute.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by childNavController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        selected = currentRoute == tab.route,
                        onClick = {
                            childNavController.navigate(tab.route) {
                                popUpTo(childNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.testTag("bottom_nav_${tab.route}")
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = childNavController,
            startDestination = BottomTabRoute.Chat.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            composable(BottomTabRoute.Chat.route) {
                ChatScreen()
            }
            composable(BottomTabRoute.Rules.route) {
                RulesScreen()
            }
            composable(BottomTabRoute.History.route) {
                HistoryScreen()
            }
            composable(BottomTabRoute.Settings.route) {
                SettingsScreen(onResetOnboarding = onResetOnboarding)
            }
        }
    }
}
