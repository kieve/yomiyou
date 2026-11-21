package ca.kieve.yomiyou.browse

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.home.services

fun megabytes(bytes: Long): String = "${(bytes + 500_000) / 1_000_000} MB"

/** The yomiage server: its books on a screen of their own. */
object ServerSource : BrowseSource {
    @Composable
    override fun Item(onStarted: () -> Unit, onOpenServer: () -> Unit) {
        val address by LocalContext.current.services.server.address.collectAsState(initial = "")
        ListItem(
            leadingContent = { Icon(painterResource(R.drawable.ic_cloud_download), contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.browse_server)) },
            supportingContent = { Text(stringResource(R.string.browse_serverSummary, address.substringAfter("://"))) },
            modifier = Modifier.clickable(onClick = onOpenServer)
        )
    }
}
