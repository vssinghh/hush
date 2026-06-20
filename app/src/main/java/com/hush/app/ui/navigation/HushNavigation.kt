package com.hush.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.hush.app.MainViewModel
import com.hush.app.ui.screens.MainScreen
import com.hush.app.ui.screens.onboarding.OnboardingScreen

@Composable
fun HushNavigation(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val startDestination = if (mainViewModel.isOnboardingCompleted) {
        ScreenRoute.Main.route
    } else {
        ScreenRoute.Onboarding.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn(tween(300)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) },
        exitTransition = { fadeOut(tween(300)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) },
        popEnterTransition = { fadeIn(tween(300)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) },
        popExitTransition = { fadeOut(tween(300)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) }
    ) {
        composable(ScreenRoute.Onboarding.route) {
            OnboardingScreen(
                onOnboardingComplete = {
                    mainViewModel.setOnboardingCompleted(true)
                    navController.navigate(ScreenRoute.Main.route) {
                        popUpTo(ScreenRoute.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(ScreenRoute.Main.route) {
            MainScreen(
                onResetOnboarding = {
                    mainViewModel.setOnboardingCompleted(false)
                    navController.navigate(ScreenRoute.Onboarding.route) {
                        popUpTo(ScreenRoute.Main.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
