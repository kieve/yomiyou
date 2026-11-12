package ca.kieve.yomiyou.browse

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.home.services

/** A way to add books, listed in Browse. */
interface BrowseSource {
    @Composable
    fun Item()
}

private val sources: List<BrowseSource> = listOf(EpubFiles)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_browse)) })
        }
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding) {
            items(sources) { it.Item() }
        }
    }
}

private object EpubFiles : BrowseSource {
    @Composable
    override fun Item() {
        val importer = LocalContext.current.services.importer
        val state by importer.state.collectAsState()
        val busy = state is Importer.State.Checking || state is Importer.State.Copying
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            importer.import(it)
        }
        Column {
            ListItem(
                leadingContent = { Icon(painterResource(R.drawable.ic_file_open), contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.browse_epubFiles)) },
                supportingContent = { Text(stringResource(R.string.browse_epubFilesSummary)) },
                modifier = Modifier.clickable(enabled = !busy) { pick.launch(arrayOf("*/*")) }
            )
            Column(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp)) {
                when (val current = state) {
                    Importer.State.Idle -> {}
                    Importer.State.Checking -> {
                        Text(stringResource(R.string.browse_checking), style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    is Importer.State.Copying -> {
                        Text(
                            stringResource(R.string.browse_copying, current.title, current.percent),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = { current.percent / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                    is Importer.State.Done -> {
                        current.added.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        current.problems.forEach {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
