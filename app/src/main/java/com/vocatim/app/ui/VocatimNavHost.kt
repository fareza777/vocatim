package com.vocatim.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vocatim.app.ui.debug.DebugScreen
import com.vocatim.app.ui.detail.DetailScreen
import com.vocatim.app.ui.home.HomeScreen
import com.vocatim.app.ui.record.RecordScreen
import com.vocatim.app.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val RECORD = "record?autoStart={autoStart}"
    const val DEBUG = "debug"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{transcriptId}"
    fun record(autoStart: Boolean = false) = "record?autoStart=$autoStart"
    fun detail(id: Long) = "detail/$id"
}

@Composable
fun VocatimNavHost(
    startRecord: Boolean = false,
    onStartRecordConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

    // Quick Settings tile: jump straight into an armed record screen.
    LaunchedEffect(startRecord) {
        if (startRecord) {
            onStartRecordConsumed()
            navController.navigate(Routes.record(autoStart = true))
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn()
        },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut()
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onRecordClick = { navController.navigate(Routes.record()) },
                onTranscriptClick = { id -> navController.navigate(Routes.detail(id)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onDebugClick = { navController.navigate(Routes.DEBUG) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.RECORD,
            arguments = listOf(
                navArgument("autoStart") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            ),
        ) { entry ->
            RecordScreen(
                autoStart = entry.arguments?.getBoolean("autoStart") ?: false,
                onFinished = { id ->
                    navController.navigate(Routes.detail(id)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("transcriptId") { type = NavType.LongType }),
        ) {
            DetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DEBUG) {
            DebugScreen()
        }
    }
}
