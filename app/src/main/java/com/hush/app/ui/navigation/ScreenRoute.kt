package com.hush.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class ScreenRoute(val route: String) {
    object Onboarding : ScreenRoute("onboarding")
    object Main : ScreenRoute("main")
}

sealed class BottomTabRoute(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    object Chat : BottomTabRoute(
        "chat", "Chat",
        Icons.AutoMirrored.Outlined.Chat,
        Icons.AutoMirrored.Filled.Chat
    )
    object Rules : BottomTabRoute(
        "rules", "Rules",
        Icons.Outlined.FilterAlt,
        Icons.Filled.FilterAlt
    )
    object History : BottomTabRoute(
        "history", "History",
        Icons.Outlined.History,
        Icons.Filled.History
    )
    object Settings : BottomTabRoute(
        "settings", "Settings",
        Icons.Outlined.Settings,
        Icons.Filled.Settings
    )
}
