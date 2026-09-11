package com.logiclabs.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.logiclabs.app.di.AppContainer
import com.logiclabs.app.ui.screens.bench.LogicLabsMainScreen
import com.logiclabs.app.ui.screens.home.HomeScreen
import com.logiclabs.app.ui.screens.settings.SettingsScreen
import com.logiclabs.app.ui.screens.splash.SplashScreen

/**
 * The app shell: splash → home → {bench, settings}.
 *
 * ## Back stack shape
 * Home is the only root. The bench and settings are always pushed *from* Home, so the
 * stack is `[home, bench]` or `[home, settings]` — the system back gesture (predictive
 * back is enabled in the manifest) pops to Home naturally, and Home's own back exits
 * the app, which is what a tool app should do.
 *
 * ## Transitions
 * Cross-fades only, on [com.logiclabs.core.designsystem.theme.Motion.DurationSheetSlide]
 * timing. Slides between full-screen destinations would put the bench's virtual light
 * source in motion, and the chassis bevels are lit from a fixed top-left — a sliding
 * bench looks like a collage, a fading one looks like a power cycle.
 */
@Composable
fun LogicLabsNavHost(container: AppContainer) {
    val navController = rememberNavController()

    val fade = tween<Float>(250)

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = { fadeIn(fade) },
        exitTransition = { fadeOut(fade) },
        popEnterTransition = { fadeIn(fade) },
        popExitTransition = { fadeOut(fade) }
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onContinue = { request -> navController.navigate(Routes.bench(request)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = Routes.benchRoute,
            arguments = listOf(
                navArgument(Routes.ARG_REQUEST) {
                    type = NavType.StringType
                    defaultValue = "continue"
                },
                navArgument(Routes.ARG_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            LogicLabsMainScreen(
                benchRequest = BenchRequest.from(
                    entry.arguments?.getString(Routes.ARG_REQUEST),
                    entry.arguments?.getString(Routes.ARG_ID)
                ),
                container = container,
                onNavigateHome = { navController.popBackStack(Routes.HOME, false) }
            )
        }

        composable(Routes.SETTINGS) {
            // Standalone settings route: the screen is also embedded as a Home tab
            // (bounded by the bottom nav bar there), so its bottom system-bar inset is
            // applied here rather than inside the composable — otherwise the tab
            // embedding would double-pad.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                SettingsScreen(
                    container = container,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
