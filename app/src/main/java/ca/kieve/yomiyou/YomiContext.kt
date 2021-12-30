package ca.kieve.yomiyou

import androidx.navigation.NavController
import ca.kieve.yomiyou.data.AppContainer

data class YomiContext(
    val navController: NavController,
    val appContainer: AppContainer
)
