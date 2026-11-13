package ca.kieve.yomiyou.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.YomiActivity
import ca.kieve.yomiyou.home.rememberLibraryPicker
import ca.kieve.yomiyou.home.services

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val services = context.services
    val folder by services.library.folder.collectAsState(initial = null)
    val dictionaries by services.dictionary.infos.collectAsState()
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
                SectionHeader(stringResource(R.string.settings_dictionary))
            }
            if (dictionaries.isEmpty()) {
                item {
                    ListItem(headlineContent = { Text(stringResource(R.string.settings_dictionaryNone)) })
                }
            }
            items(dictionaries, key = { it.id }) { stored ->
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.settings_dictionaryEntries, stored.title, stored.entryCount))
                    },
                    supportingContent = stored.attribution.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                    trailingContent = {
                        Button(
                            onClick = { services.dictionary.clear(stored) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text(stringResource(R.string.settings_dictionaryClear)) }
                    },
                )
            }
            item {
                SectionHeader(stringResource(R.string.settings_debug))
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
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
    )
}
