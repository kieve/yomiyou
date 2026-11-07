package ca.kieve.yomiyou.shelf

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.home.EmptyMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen() {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name)) })
        }
    ) { innerPadding ->
        EmptyMessage(stringResource(R.string.shelf_empty), Modifier.padding(innerPadding))
    }
}
