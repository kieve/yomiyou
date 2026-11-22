package ca.kieve.yomiyou.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.YomiActivity
import ca.kieve.yomiyou.home.rememberLibraryPicker
import ca.kieve.yomiyou.home.services
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val services = context.services
    val folder by services.library.folder.collectAsState(initial = null)
    val dictionaries by services.dictionary.infos.collectAsState()
    val chooseFolder = rememberLibraryPicker()
    val address by services.server.address.collectAsState(initial = "")
    var editingAddress by remember { mutableStateOf(false) }
    val kavitaAddress by services.kavita.address.collectAsState(initial = "")
    val kavitaKey by services.kavita.key.collectAsState(initial = "")
    var editingKavita by remember { mutableStateOf<KavitaField?>(null) }
    val scope = rememberCoroutineScope()

    if (editingAddress) {
        TextDialog(
            title = stringResource(R.string.settings_serverAddress),
            value = address,
            onSave = {
                editingAddress = false
                scope.launch { services.server.setAddress(it) }
            },
            onDismiss = { editingAddress = false },
        )
    }
    editingKavita?.let { field ->
        TextDialog(
            title = stringResource(if (field == KavitaField.Address) R.string.settings_kavitaAddress else R.string.settings_kavitaKey),
            value = if (field == KavitaField.Address) kavitaAddress else "",
            secret = field == KavitaField.Key,
            onSave = {
                editingKavita = null
                scope.launch {
                    if (field == KavitaField.Address) services.kavita.setAddress(it) else services.kavita.setKey(it)
                }
            },
            onDismiss = { editingKavita = null },
        )
    }

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
                SectionHeader(stringResource(R.string.settings_server))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_serverAddress)) },
                    supportingContent = { Text(address) },
                    modifier = Modifier.clickable { editingAddress = true }
                )
            }
            item {
                SectionHeader(stringResource(R.string.settings_kavita))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_kavitaAddress)) },
                    supportingContent = { Text(kavitaAddress) },
                    modifier = Modifier.clickable { editingKavita = KavitaField.Address }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_kavitaKey)) },
                    supportingContent = {
                        Text(stringResource(if (kavitaKey.isEmpty()) R.string.settings_kavitaKeyUnset else R.string.settings_kavitaKeySet))
                    },
                    modifier = Modifier.clickable { editingKavita = KavitaField.Key }
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

private enum class KavitaField { Address, Key }

/** One setting typed in: an address, or with [secret] a key, hidden as it's typed and never shown back. */
@Composable
private fun TextDialog(
    title: String,
    value: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    secret: Boolean = false,
) {
    var text by remember { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.settings_serverSave))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_serverCancel)) }
        },
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
