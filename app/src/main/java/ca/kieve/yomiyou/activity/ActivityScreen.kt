package ca.kieve.yomiyou.activity

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.kieve.yomiyou.R
import ca.kieve.yomiyou.home.EmptyMessage
import ca.kieve.yomiyou.home.services

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen() {
    val log = LocalContext.current.services.activity
    val entries by log.entries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_activity)) },
                actions = {
                    TextButton(
                        onClick = log::clear,
                        enabled = entries.any { it.state != ActivityEntry.State.Running }
                    ) { Text(stringResource(R.string.activity_clear)) }
                }
            )
        }
    ) { innerPadding ->
        if (entries.isEmpty()) {
            EmptyMessage(stringResource(R.string.activity_empty), Modifier.padding(innerPadding))
            return@Scaffold
        }
        LazyColumn(contentPadding = innerPadding) {
            items(entries, key = { it.id }) { entry ->
                Entry(entry)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Entry(entry: ActivityEntry) {
    val body = MaterialTheme.typography.bodyMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                ago(entry.finished ?: entry.started),
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )
        }
        when (entry.state) {
            ActivityEntry.State.Running -> {
                entry.doing?.let { Text(it, style = body, color = muted) }
                val percent = entry.percent
                val bar = Modifier.fillMaxWidth().padding(top = 8.dp)
                if (percent == null) LinearProgressIndicator(bar) else LinearProgressIndicator({ percent / 100f }, bar)
            }
            ActivityEntry.State.Done -> {
                entry.added.forEach { Text(it, style = body) }
                entry.problems.forEach { Text(it, style = body, color = MaterialTheme.colorScheme.error) }
            }
            ActivityEntry.State.Interrupted ->
                Text(stringResource(R.string.activity_interrupted), style = body, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ago(time: Long): String =
    if (System.currentTimeMillis() - time < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.activity_justNow)
    } else {
        DateUtils.getRelativeTimeSpanString(time).toString()
    }
