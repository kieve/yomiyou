package ca.kieve.yomiyou.home

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.browse.BrowseScreen
import ca.kieve.yomiyou.settings.SettingsScreen
import ca.kieve.yomiyou.shelf.ShelfScreen

enum class Tab(@StringRes val label: Int, @DrawableRes val icon: Int, @DrawableRes val selectedIcon: Int) {
    Shelf(R.string.home_shelf, R.drawable.ic_shelf, R.drawable.ic_shelf_filled),
    Browse(R.string.home_browse, R.drawable.ic_browse, R.drawable.ic_browse_filled),
    Settings(R.string.home_settings, R.drawable.ic_settings, R.drawable.ic_settings_filled),
}

@Composable
fun TabsScreen(onOpenBook: (String) -> Unit) {
    var current by rememberSaveable { mutableStateOf(Tab.Shelf) }
    val tabStates = rememberSaveableStateHolder()

    BackHandler(enabled = current != Tab.Shelf) {
        current = Tab.Shelf
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                item(
                    selected = selected,
                    onClick = { current = tab },
                    icon = {
                        Icon(
                            painter = painterResource(if (selected) tab.selectedIcon else tab.icon),
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(tab.label)) }
                )
            }
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            tabStates.SaveableStateProvider(current.name) {
                when (current) {
                    Tab.Shelf -> ShelfScreen(onOpenBook)
                    Tab.Browse -> BrowseScreen()
                    Tab.Settings -> SettingsScreen()
                }
            }
            MiniPlayer(
                onOpen = onOpenBook,
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
            )
        }
    }
}
