package ca.kieve.yomiyou.ui.composable.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.YomiContext
import ca.kieve.yomiyou.data.repository.NovelRepository
import ca.kieve.yomiyou.util.clearFocusOnKeyboardDismiss

@Composable
fun NovelSearchScreen(yomiContext: YomiContext) {
    val novelRepository = yomiContext.appContainer.novelRepository

    SearchBar(novelRepository)
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
            value = text.value,
            placeholder = {
                Text(stringResource(R.string.novelSearch_placeholder))
            },
            onValueChange = { value: TextFieldValue -> text.value = value },
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
private fun SearchResults() {
    // TODO: Show loading icon when loading.
    //       Show results when loaded
}


