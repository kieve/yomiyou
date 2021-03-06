package ca.kieve.yomiyou

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ca.kieve.yomiyou.data.AppContainer
import ca.kieve.yomiyou.ui.composable.screen.NovelInfoScreen
import ca.kieve.yomiyou.ui.composable.screen.ReaderScreen
import ca.kieve.yomiyou.ui.composable.screen.NovelListScreen
import ca.kieve.yomiyou.ui.composable.screen.NovelSearchScreen

@Composable
fun YomiNavigation(appContainer: AppContainer) {
    val navController = rememberNavController()
    val yomiContext = YomiContext(
        navController = navController,
        appContainer = appContainer)

    NavHost(navController = navController, startDestination = YomiScreen.NovelListNav.route) {
        composable(route = YomiScreen.NovelListNav.route) {
            NovelListScreen(yomiContext)
        }
        composable(route = YomiScreen.NovelSearchNav.route) {
            NovelSearchScreen(yomiContext)
        }
        composable(
            route = YomiScreen.NovelInfoNav.route + "/{novelId}",
            arguments = listOf(
                navArgument("novelId") {
                    type = NavType.LongType
                }
            )
        ) { entry ->
            NovelInfoScreen(
                yomiContext = yomiContext,
                novelId = entry.arguments?.getLong("novelId") ?: 0
            )
        }
        composable(
            route = YomiScreen.ReaderNav.route + "/{novelId}/{chapterId}",
            arguments = listOf(
                navArgument("novelId") {
                    type = NavType.LongType
                },
                navArgument("chapterId") {
                    type = NavType.LongType
                }
            )
        ) { entry ->
            ReaderScreen(
                yomiContext = yomiContext,
                novelId = entry.arguments?.getLong("novelId") ?: 0,
                chapterId = entry.arguments?.getLong("chapterId") ?: 0
            )
        }
    }
}
