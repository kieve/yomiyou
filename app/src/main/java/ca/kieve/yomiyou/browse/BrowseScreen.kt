package ca.kieve.yomiyou.browse

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.home.EmptyMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_browse)) })
        }
    ) { innerPadding ->
        EmptyMessage(stringResource(R.string.browse_empty), Modifier.padding(innerPadding))
    }
}
