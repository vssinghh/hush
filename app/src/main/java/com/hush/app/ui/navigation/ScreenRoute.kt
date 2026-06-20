package com.hush.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class ScreenRoute(val route: String) {
    object Onboarding : ScreenRoute("onboarding")
    object Main : ScreenRoute("main")
}

sealed class BottomTabRoute(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Chat : BottomTabRoute("chat", "Chat", Icons.Default.Send)
    object Rules : BottomTabRoute("rules", "Rules", Icons.Default.List)
    object History : BottomTabRoute("history", "History", Icons.Default.DateRange)
    object Settings : BottomTabRoute("settings", "Settings", Icons.Default.Settings)
}
