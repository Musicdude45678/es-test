package com.reps.app.ui.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.data.sync.ServerInfo
import com.reps.app.ui.LocalContainer
import com.reps.app.ui.LocalSnackbar
import kotlinx.coroutines.launch

/**
 * Handles reps://connect links opened from the dashboard: confirms, saves the connection,
 * and offers to restore the server backup when this phone has no data yet.
 */
@Composable
fun ConnectDialogHost() {
    val container = LocalContainer.current
    val request by container.pendingConnect.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var busy by remember { mutableStateOf(false) }
    var restoreOffer by remember { mutableStateOf<ServerInfo?>(null) }

    request?.let { req ->
        AlertDialog(
            onDismissRequest = { if (!busy) container.pendingConnect.value = null },
            title = { Text("Connect to your dashboard?") },
            text = {
                Column {
                    Text("Reps will back up your workouts to:")
                    Spacer(Modifier.height(6.dp))
                    Text(req.url, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    if (req.name.isNotBlank()) Text("Account: ${req.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val result = runCatching { container.sync.testConnection(req.url, req.token) }
                        result.onSuccess { info ->
                            container.settings.setSyncConfig(req.url, req.token)
                            val localCount = container.database.workoutDao().completedCount()
                            if (info.snapshotCount > 0 && localCount == 0) restoreOffer = info
                            else {
                                runCatching { container.sync.push() }
                                snackbar.showSnackbar("Connected to your dashboard")
                            }
                        }.onFailure { snackbar.showSnackbar(it.message ?: "Could not connect") }
                        busy = false
                        container.pendingConnect.value = null
                    }
                }) { Text(if (busy) "Connecting…" else "Connect") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { container.pendingConnect.value = null }) { Text("Cancel") } }
        )
    }

    restoreOffer?.let { info ->
        AlertDialog(
            onDismissRequest = { restoreOffer = null },
            title = { Text("Restore your backup?") },
            text = { Text("Your dashboard has ${info.snapshotCount} backup${if (info.snapshotCount == 1) "" else "s"}. Restore the latest one onto this phone?") },
            confirmButton = {
                Button(onClick = {
                    restoreOffer = null
                    scope.launch {
                        val r = runCatching { container.sync.restoreLatest() }
                        snackbar.showSnackbar(r.fold({ s -> if (s == null) "No backup found" else "Restored ${s.workouts} workouts" }, { it.message ?: "Restore failed" }))
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { restoreOffer = null; scope.launch { runCatching { container.sync.push() } } }) { Text("Start fresh") } }
        )
    }
}
