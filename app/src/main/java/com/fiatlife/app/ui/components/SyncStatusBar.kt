package com.fiatlife.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Persistent strip shown while background relay publishes are in flight or have
 * failed. Failures expose a Retry action; pending shows a spinner. Renders
 * nothing when everything is synced.
 */
@Composable
fun SyncStatusBar(
    pending: Int,
    failed: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pending <= 0 && failed <= 0) return

    val isError = failed > 0
    val bg = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isError) {
            Text(
                text = "$failed change${if (failed == 1) "" else "s"} not synced",
                color = fg,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        } else {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = fg,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Syncing $pending change${if (pending == 1) "" else "s"}…",
                color = fg,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
