package com.vocatim.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vocatim.app.ui.debug.DebugScreen
import com.vocatim.app.ui.detail.DetailScreen
import com.vocatim.app.ui.home.HomeScreen
import com.vocatim.app.ui.record.RecordScreen

object Routes {
    const val HOME = "home"
    const val RECORD = "record"
    const val DEBUG = "debug"
    const val DETAIL = "detail/{transcriptId}"
    fun detail(id: Long) = "detail/$id"
}

@Composable
fun VocatimNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onRecordClick = { navController.navigate(Routes.RECORD) },
                onTranscriptClick = { id -> navController.navigate(Routes.detail(id)) },
                onDebugClick = { navController.navigate(Routes.DEBUG) },
            )
        }
        composable(Routes.RECORD) {
            RecordScreen(
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
