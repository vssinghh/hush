package com.hush.app.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                val navBackStackEntry by childNavController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                tab.title,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        },
                        selected = selected,
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
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
            // Gentle fade + settle between tabs — modern, not distracting
            enterTransition = {
                fadeIn(tween(220)) + scaleIn(initialScale = 0.985f, animationSpec = tween(220))
            },
            exitTransition = { fadeOut(tween(120)) }
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
