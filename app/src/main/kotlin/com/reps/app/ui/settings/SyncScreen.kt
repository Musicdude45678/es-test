package com.reps.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.data.prefs.Settings
import com.reps.app.data.sync.SyncState
import com.reps.app.domain.Dates
import com.reps.app.ui.LocalContainer
import com.reps.app.ui.LocalSnackbar
import com.reps.app.ui.components.ConfirmDialog
import com.reps.app.ui.components.KeyValueRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val syncState by container.sync.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current

    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var seeded by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    var showDisconnect by remember { mutableStateOf(false) }

    LaunchedEffect(settings.syncUrl, settings.syncToken) {
        if (!seeded && (settings.syncUrl.isNotEmpty() || settings.syncToken.isNotEmpty())) { url = settings.syncUrl; token = settings.syncToken; seeded = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = { Text("Dashboard & backup") }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (settings.syncConfigured) Icons.Default.CloudDone else Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(if (settings.syncConfigured) "Connected" else "Not connected", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        if (syncState is SyncState.Syncing || busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Every workout is backed up to your Reps dashboard a few seconds after you log it, and you can restore everything on a new phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (settings.syncConfigured) {
                        Spacer(Modifier.height(12.dp))
                        KeyValueRow("Last backup", if (settings.lastSyncAt > 0) "${Dates.relativeDay(settings.lastSyncAt)} ${Dates.time(settings.lastSyncAt)}" else "never")
                        Spacer(Modifier.height(4.dp))
                        KeyValueRow("Pending changes", if (settings.syncDirty) "yes" else "no")
                        if (settings.lastSyncError.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(settings.lastSyncError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Text(
                "Fastest way to connect: open your dashboard on this phone, go to Connect phone, and tap the link. Or type the details below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = url, onValueChange = { url = it }, label = { Text("Dashboard URL") }, placeholder = { Text("https://reps.edgestudios.co.za") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            OutlinedTextField(
                value = token, onValueChange = { token = it }, label = { Text("Sync token") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { showToken = !showToken }) { Icon(if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null) } }
            )
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        val result = runCatching { container.sync.testConnection(url, token) }
                        result.onSuccess { info ->
                            container.settings.setSyncConfig(url, token)
                            snackbar.showSnackbar("Connected as ${info.displayName.ifBlank { info.user }}" + if (info.snapshotCount > 0) " · ${info.snapshotCount} backups on server" else "")
                            if (info.snapshotCount == 0) runCatching { container.sync.push() }
                        }.onFailure { snackbar.showSnackbar(it.message ?: "Could not connect") }
                        busy = false
                    }
                },
                enabled = url.isNotBlank() && token.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)
            ) { Text(if (settings.syncConfigured) "Save & test connection" else "Connect") }

            if (settings.syncConfigured) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic backup", style = MaterialTheme.typography.bodyLarge)
                        Text("Push changes shortly after every edit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.autoSync, onCheckedChange = { scope.launch { container.settings.setAutoSync(it) } })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { scope.launch { busy = true; val r = runCatching { container.sync.push() }; busy = false; snackbar.showSnackbar(r.fold({ "Backed up (${it.snapshotCount} snapshots on server)" }, { it.message ?: "Backup failed" })) } },
                        enabled = !busy, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp)
                    ) { Text("Back up now") }
                    OutlinedButton(
                        onClick = { showRestore = true }, enabled = !busy,
                        modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp)
                    ) { Text("Restore") }
                }
                TextButton(onClick = { showDisconnect = true }, modifier = Modifier.fillMaxWidth()) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showRestore) {
        ConfirmDialog(
            title = "Restore from dashboard?",
            body = "Everything on this phone will be replaced with the latest backup on the server.",
            confirmLabel = "Restore", destructive = true,
            onConfirm = {
                showRestore = false
                scope.launch {
                    busy = true
                    val r = runCatching { container.sync.restoreLatest() }
                    busy = false
                    snackbar.showSnackbar(r.fold({ s -> if (s == null) "The server has no backup yet" else "Restored ${s.workouts} workouts and ${s.sets} sets" }, { it.message ?: "Restore failed" }))
                }
            },
            onDismiss = { showRestore = false }
        )
    }
    if (showDisconnect) {
        ConfirmDialog(
            title = "Disconnect?", body = "Backups stop until you connect again. Nothing on the server is deleted.", confirmLabel = "Disconnect", destructive = true,
            onConfirm = { showDisconnect = false; scope.launch { container.settings.clearSyncConfig(); url = ""; token = "" } },
            onDismiss = { showDisconnect = false }
        )
    }
}
