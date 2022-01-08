package ca.kieve.yomiyou.ui.composable.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.YomiContext
import ca.kieve.yomiyou.YomiScreen
import ca.kieve.yomiyou.data.repository.NovelRepository
import ca.kieve.yomiyou.ui.composable.NovelCard
import ca.kieve.yomiyou.util.clearFocusOnKeyboardDismiss

@Composable
fun NovelSearchScreen(yomiContext: YomiContext) {
    val navController = yomiContext.navController
    val novelRepository = yomiContext.appContainer.novelRepository

    val searchInProgress by novelRepository.searchInProgress.collectAsState()

    Column {
        SearchBar(novelRepository)
        if (searchInProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            SearchResults(
                navController = navController,
                novelRepository = novelRepository
            )
        }
    }
}

@Composable
private fun SearchBar(novelRepository: NovelRepository) {
    val text = remember {
        mutableStateOf(TextFieldValue())
    }
    val focusManager = LocalFocusManager.current

    Surface(modifier = Modifier.fillMaxWidth()) {
        TextField(
            modifier = Modifier
                .clearFocusOnKeyboardDismiss(),
            singleLine = true,
            maxLines = 1,
            value = text.value,
            placeholder = {
                Text(stringResource(R.string.novelSearch_placeholder))
            },
            onValueChange = { value: TextFieldValue ->
                val sanitized = value.text.replace("\n", "")
                if (sanitized.length >= value.text.length) {
                    text.value = value
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    novelRepository.searchForNewNovels(text.value.text)
                    focusManager.clearFocus()
                },
                onDone = {
                    focusManager.clearFocus()
                }
            )
        )
    }
}

@Composable
private fun SearchResults(
    navController: NavController,
    novelRepository: NovelRepository
) {
    val results by novelRepository.searchResults.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(results.values.toList()) { novelInfo ->
            NovelCard(
                title = novelInfo.novelInfo.title ?: "Unknown Novel",
                subTitle = "TODO",
                coverFile = novelInfo.coverFile,
                modifier = Modifier
                    .clickable {
                        navController.navigate(
                            YomiScreen.NovelInfoNav.withArgs(
                                novelInfo.tempId.toString()
                            )
                        )
                    }
            )
        }
    }
}


