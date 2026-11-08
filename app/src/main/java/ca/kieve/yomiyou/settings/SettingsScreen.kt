package ca.kieve.yomiyou.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.YomiActivity
import ca.kieve.yomiyou.book.TestBook
import ca.kieve.yomiyou.home.rememberLibraryPicker
import ca.kieve.yomiyou.home.services

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenBook: (String) -> Unit) {
    val context = LocalContext.current
    val services = context.services
    val folder by services.library.folder.collectAsState(initial = null)
    val chooseFolder = rememberLibraryPicker()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_settings)) })
        }
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding) {
            item {
                SectionHeader(stringResource(R.string.settings_library))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_libraryFolder)) },
                    supportingContent = { folder?.let { Text(services.library.describe(it)) } },
                    modifier = Modifier.clickable(onClick = chooseFolder)
                )
            }
            item {
                SectionHeader(stringResource(R.string.settings_debug))
            }
            if (services.testBook.bundled) {
                item {
                    TestBookItem(services.testBook, onOpenBook)
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_webnovelScreens)) },
                    supportingContent = { Text(stringResource(R.string.settings_webnovelScreensSummary)) },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, YomiActivity::class.java))
                    }
                )
            }
        }
    }
}

@Composable
private fun TestBookItem(testBook: TestBook, onOpenBook: (String) -> Unit) {
    val state by testBook.state.collectAsState()
    var opening by remember { mutableStateOf(false) }
    LaunchedEffect(state, opening) {
        val ready = state as? TestBook.State.Ready ?: return@LaunchedEffect
        if (opening) {
            opening = false
            onOpenBook(ready.bookId)
        }
    }
    val summary = when (val current = state) {
        is TestBook.State.Copying -> stringResource(R.string.settings_testBookCopying, current.percent)
        is TestBook.State.Failed -> current.message
        else -> stringResource(R.string.settings_testBookSummary)
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_testBook)) },
        supportingContent = { Text(summary) },
        modifier = Modifier.clickable {
            opening = true
            testBook.prepare()
        }
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
    )
}
