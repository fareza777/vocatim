package com.vocatim.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vocatim.app.ads.MonetizedBanner
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
    const val PAYWALL = "paywall"
    const val CALENDAR = "calendar"
    const val TRASH = "trash"
    const val DETAIL = "detail/{transcriptId}"
    fun record(autoStart: Boolean = false) = "record?autoStart=$autoStart"
    fun detail(id: Long) = "detail/$id"
}

@Composable
fun VocatimNavHost(
    startRecord: Boolean = false,
    onStartRecordConsumed: () -> Unit = {},
    openTranscriptId: Long? = null,
    onOpenTranscriptConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

    LaunchedEffect(openTranscriptId) {
        openTranscriptId?.let { id ->
            onOpenTranscriptConsumed()
            navController.navigate(Routes.detail(id))
        }
    }

    // Quick Settings tile: jump straight into an armed record screen.
    LaunchedEffect(startRecord) {
        if (startRecord) {
            onStartRecordConsumed()
            navController.navigate(Routes.record(autoStart = true))
        }
    }

    val currentRoute by navController.currentBackStackEntryAsState()
    val route = currentRoute?.destination?.route
    // Collapse on Record only. Unmounting the banner on Settings/Paywall
    // destroyed the AdView; the next load often came back empty.
    val hideBanner = route?.startsWith("record") == true

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            MonetizedBanner(hidden = hideBanner)
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
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
                onCalendarClick = { navController.navigate(Routes.CALENDAR) },
                onDebugClick = { navController.navigate(Routes.DEBUG) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onUpgradeClick = { navController.navigate(Routes.PAYWALL) },
                onTrashClick = { navController.navigate(Routes.TRASH) },
            )
        }
        composable(Routes.TRASH) {
            com.vocatim.app.ui.trash.TrashScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.PAYWALL) {
            com.vocatim.app.ui.paywall.PaywallScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CALENDAR) {
            com.vocatim.app.ui.calendar.CalendarScreen(
                onBack = { navController.popBackStack() },
                onTranscriptClick = { id -> navController.navigate(Routes.detail(id)) },
            )
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
        ) { entry ->
            DetailScreen(
                onBack = { navController.popBackStack() },
                viewModel = androidx.hilt.navigation.compose.hiltViewModel(entry),
            )
        }
        composable(Routes.DEBUG) {
            DebugScreen()
        }
        }
    }
}
