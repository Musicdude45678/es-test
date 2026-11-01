package com.reps.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.data.model.ThemeMode
import com.reps.app.data.model.WeightUnit
import com.reps.app.data.prefs.Settings
import com.reps.app.domain.Calc
import com.reps.app.domain.Dates
import com.reps.app.ui.LocalContainer
import com.reps.app.ui.LocalSnackbar
import com.reps.app.ui.components.ConfirmDialog
import com.reps.app.ui.components.NumberPadSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenSync: () -> Unit) {
    val container = LocalContainer.current
    val settingsRepo = container.settings
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val unit = settings.unit

    var showBarPad by remember { mutableStateOf(false) }
    var showIncrement by remember { mutableStateOf(false) }
    var showRest by remember { mutableStateOf(false) }
    var showRestPad by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }

    fun share(fileName: String, mime: String, content: String) {
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                val f = File(dir, fileName)
                f.writeText(content)
                FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Export"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = { Text("Settings") }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            // Cloud backup card
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clickable(onClick = onOpenSync),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (settings.syncConfigured) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (settings.syncConfigured) Icons.Default.Cloud else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = if (settings.syncConfigured) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Dashboard & backup", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                !settings.syncConfigured -> "Not connected. Tap to set up."
                                settings.lastSyncError.isNotBlank() -> "Problem: ${settings.lastSyncError}"
                                settings.lastSyncAt > 0 -> "Backed up ${Dates.relativeDay(settings.lastSyncAt).lowercase()} at ${Dates.time(settings.lastSyncAt)}" + if (settings.syncDirty) " · changes pending" else ""
                                else -> "Connected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (settings.lastSyncError.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Group("Lifting") {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text("Units", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        WeightUnit.values().forEachIndexed { i, u ->
                            SegmentedButton(
                                selected = unit == u,
                                onClick = { scope.launch { settingsRepo.setUnit(u) } },
                                shape = SegmentedButtonDefaults.itemShape(i, WeightUnit.values().size)
                            ) { Text(u.label) }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow("Default bar weight", "Used for barbell exercises unless an exercise overrides it", Calc.formatWeight(settings.defaultBarWeightKg, unit)) { showBarPad = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow("Weight step", "How much − and + change the weight", "${Calc.formatNumber(settings.weightIncrement)} ${unit.label}") { showIncrement = true }
            }

            Group("Rest timer") {
                SettingRow("Rest duration", "Countdown after each working set", formatRest(settings.restTimerSeconds)) { showRest = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow("Start automatically", "Begin the countdown when you log a set", settings.autoRestTimer) { scope.launch { settingsRepo.setAutoRestTimer(it) } }
            }

            Group("During workouts") {
                SwitchRow("Keep screen on", "Never let the screen lock mid-set", settings.keepScreenOn) { scope.launch { settingsRepo.setKeepScreenOn(it) } }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow("Vibration", "Buzz when a set is logged and when rest ends", settings.haptics) { scope.launch { settingsRepo.setHaptics(it) } }
            }

            Group("Appearance") {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.values().forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = settings.theme == m,
                                onClick = { scope.launch { settingsRepo.setTheme(m) } },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.values().size)
                            ) { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        }
                    }
                }
            }

            Group("Your data") {
                SettingRow("Export spreadsheet (CSV)", "Every set you've logged, one row each", null) {
                    scope.launch { share("reps-sets.csv", "text/csv", container.snapshots.exportCsv(settings)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow("Export backup file (JSON)", "Full backup you can import later", null) {
                    scope.launch { share("reps-backup.json", "application/json", container.snapshots.exportJson()) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow("Import backup file", "Replaces everything on this phone", null) { importLauncher.launch(arrayOf("application/json", "*/*")) }
            }

            Text(
                "Reps ${container.appVersion}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
    }

    if (showBarPad) {
        NumberPadSheet(
            title = "Default bar weight", initial = unit.fromKg(settings.defaultBarWeightKg), unitLabel = unit.label, allowDecimal = true,
            quickValues = if (unit == WeightUnit.KG) listOf(20.0, 15.0, 10.0, 7.5) else listOf(45.0, 35.0, 25.0, 15.0),
            onConfirm = { scope.launch { settingsRepo.setDefaultBarWeightKg(unit.toKg(it)) }; showBarPad = false },
            onDismiss = { showBarPad = false }
        )
    }

    if (showIncrement) {
        val choices = if (unit == WeightUnit.KG) listOf(0.5, 1.0, 1.25, 2.5, 5.0) else listOf(1.0, 2.5, 5.0, 10.0)
        AlertDialog(
            onDismissRequest = { showIncrement = false },
            title = { Text("Weight step") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { c ->
                        FilterChip(selected = settings.weightIncrement == c, onClick = { scope.launch { settingsRepo.setWeightIncrement(c) }; showIncrement = false }, label = { Text("${Calc.formatNumber(c)} ${unit.label}") })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showIncrement = false }) { Text("Done") } }
        )
    }

    if (showRest) {
        AlertDialog(
            onDismissRequest = { showRest = false },
            title = { Text("Rest duration") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60, 90, 120, 150, 180, 240, 300).forEach { s ->
                        FilterChip(selected = settings.restTimerSeconds == s, onClick = { scope.launch { settingsRepo.setRestTimerSeconds(s) }; showRest = false }, label = { Text(formatRest(s)) })
                    }
                    FilterChip(selected = false, onClick = { showRest = false; showRestPad = true }, label = { Text("Custom…") })
                }
            },
            confirmButton = { TextButton(onClick = { showRest = false }) { Text("Done") } }
        )
    }
    if (showRestPad) {
        NumberPadSheet(
            title = "Rest (seconds)", initial = settings.restTimerSeconds.toDouble(), unitLabel = "s", allowDecimal = false,
            onConfirm = { scope.launch { settingsRepo.setRestTimerSeconds(it.toInt().coerceIn(5, 3600)) }; showRestPad = false },
            onDismiss = { showRestPad = false }
        )
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = "Import backup?",
            body = "Everything currently on this phone will be replaced by the backup file.",
            confirmLabel = "Import", destructive = true,
            onConfirm = {
                pendingImport = null
                scope.launch {
                    val result = runCatching {
                        val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
                        container.snapshots.restore(container.snapshots.parse(text))
                    }
                    snackbar.showSnackbar(result.fold({ "Imported ${it.workouts} workouts" }, { "Import failed: ${it.message ?: "unreadable file"}" }))
                }
            },
            onDismiss = { pendingImport = null }
        )
    }
}

private fun formatRest(seconds: Int): String = if (seconds % 60 == 0) "${seconds / 60} min" else if (seconds > 60) "${seconds / 60}:${"%02d".format(seconds % 60)}" else "$seconds s"

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 6.dp))
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) { Column { content() } }
}

@Composable
private fun SettingRow(title: String, subtitle: String?, value: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        else Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
