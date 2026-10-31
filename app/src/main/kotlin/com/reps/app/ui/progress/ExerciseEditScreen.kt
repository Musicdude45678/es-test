package com.reps.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.reps.app.data.db.ExerciseEntity
import com.reps.app.data.model.Equipment
import com.reps.app.data.model.MuscleGroup
import com.reps.app.data.prefs.Settings
import com.reps.app.domain.Calc
import com.reps.app.ui.LocalContainer
import com.reps.app.ui.LocalSnackbar
import com.reps.app.ui.components.ConfirmDialog
import com.reps.app.ui.components.NumberPadSheet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditScreen(exerciseId: String?, onBack: () -> Unit) {
    val container = LocalContainer.current
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current

    var loaded by remember { mutableStateOf(exerciseId == null) }
    var existing by remember { mutableStateOf<ExerciseEntity?>(null) }
    var settings by remember { mutableStateOf(Settings()) }
    var name by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf(Equipment.BARBELL) }
    var muscle by remember { mutableStateOf(MuscleGroup.CHEST) }
    var barOverride by remember { mutableStateOf<Double?>(null) }
    var archived by remember { mutableStateOf(false) }
    var showBarPad by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(exerciseId) {
        settings = container.settings.settings.first()
        if (exerciseId != null) {
            repo.getExercise(exerciseId)?.let { e ->
                existing = e; name = e.name; equipment = e.equipment; muscle = e.muscleGroup; barOverride = e.barWeightKg; archived = e.isArchived
            }
        }
        loaded = true
    }

    val unit = settings.unit
    val defaultBar = equipment.defaultBarKg(settings.defaultBarWeightKg)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = { Text(if (exerciseId == null) "New exercise" else "Edit exercise") }
            )
        }
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))

            Column {
                Text("Equipment", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Equipment.values().forEach { e -> FilterChip(selected = equipment == e, onClick = { equipment = e }, label = { Text(e.label) }) }
                }
            }
            Column {
                Text("Muscle group", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MuscleGroup.values().forEach { g -> FilterChip(selected = muscle == g, onClick = { muscle = g }, label = { Text(g.label) }) }
                }
            }
            if (equipment.usesBar) {
                Column {
                    Text("Bar weight", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Counted as part of the total weight you log.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = barOverride == null, onClick = { barOverride = null }, label = { Text("Default (${Calc.formatWeight(defaultBar, unit)})") })
                        listOf(20.0, 15.0, 10.0, 7.5).forEach { b ->
                            FilterChip(selected = barOverride == b, onClick = { barOverride = b }, label = { Text(Calc.formatWeight(b, unit)) })
                        }
                        FilterChip(
                            selected = barOverride != null && barOverride !in listOf(20.0, 15.0, 10.0, 7.5),
                            onClick = { showBarPad = true },
                            label = { Text(if (barOverride != null && barOverride !in listOf(20.0, 15.0, 10.0, 7.5)) "Custom: ${Calc.formatWeight(barOverride!!, unit)}" else "Custom…") }
                        )
                    }
                }
            }
            if (existing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Archived", style = MaterialTheme.typography.bodyLarge)
                        Text("Hidden from the exercise picker. History is kept.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = archived, onCheckedChange = { archived = it })
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        val e = existing
                        if (e == null) repo.createExercise(name, equipment, muscle, barOverride)
                        else repo.updateExercise(e.copy(name = name.trim(), equipment = equipment, muscleGroup = muscle, barWeightKg = barOverride, isArchived = archived))
                        onBack()
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text(if (existing == null) "Create exercise" else "Save changes", style = MaterialTheme.typography.titleMedium) }

            if (existing != null) {
                OutlinedButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete exercise") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showBarPad) {
        NumberPadSheet(
            title = "Bar weight",
            initial = unit.fromKg(barOverride ?: defaultBar),
            unitLabel = unit.label,
            allowDecimal = true,
            onConfirm = { barOverride = unit.toKg(it); showBarPad = false },
            onDismiss = { showBarPad = false }
        )
    }

    if (showDelete) {
        ConfirmDialog(
            title = "Delete exercise?",
            body = "If you have logged this exercise before it will be archived instead, so your history stays intact.",
            confirmLabel = "Delete", destructive = true,
            onConfirm = {
                showDelete = false
                scope.launch {
                    val deleted = repo.deleteOrArchiveExercise(exerciseId!!)
                    snackbar.showSnackbar(if (deleted) "Exercise deleted" else "Exercise archived (it has history)")
                    onBack()
                }
            },
            onDismiss = { showDelete = false }
        )
    }
}
