package com.hush.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Floating pill navigation — a single quiet island instead of a
            // full-width bar. Selected tab expands into a filled pill.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    val navBackStackEntry by childNavController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    Row(
                        modifier = Modifier.padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEach { tab ->
                            NavPill(
                                tab = tab,
                                selected = currentRoute == tab.route,
                                onClick = {
                                    childNavController.navigate(tab.route) {
                                        popUpTo(childNavController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = childNavController,
            startDestination = BottomTabRoute.Chat.route,
            modifier = Modifier.padding(innerPadding),
            // Gentle fade + settle between tabs — calm, not distracting
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

@Composable
private fun NavPill(
    tab: BottomTabRoute,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("bottom_nav_${tab.route}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .animateContentSize(animationSpec = tween(200))
                .padding(horizontal = if (selected) 16.dp else 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.icon,
                contentDescription = tab.title,
                modifier = Modifier.size(20.dp)
            )
            if (selected) {
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
