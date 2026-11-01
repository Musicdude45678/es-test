package com.reps.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reps.app.data.db.ExerciseEntity
import com.reps.app.data.db.SetEntity
import com.reps.app.data.prefs.Settings
import com.reps.app.domain.Calc
import com.reps.app.ui.components.BarOptions
import com.reps.app.ui.components.NumberPadSheet
import com.reps.app.ui.components.PlateBreakdown
import com.reps.app.ui.components.Stepper

/** Edit or delete an already logged set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetEditSheet(
    set: SetEntity,
    exercise: ExerciseEntity,
    settings: Settings,
    title: String = "Edit set ${set.position + 1}",
    onSave: (SetEntity) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var weightKg by remember { mutableStateOf(set.weightKg) }
    var reps by remember { mutableStateOf(set.reps) }
    var warmup by remember { mutableStateOf(set.isWarmup) }
    var barKg by remember { mutableStateOf(set.barWeightKg ?: if (exercise.equipment.usesBar) (exercise.barWeightKg ?: exercise.equipment.defaultBarKg(settings.defaultBarWeightKg)) else null) }
    var editing by remember { mutableStateOf<String?>(null) }
    val unit = settings.unit

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(exercise.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stepper(
                    label = "Weight",
                    value = Calc.formatWeight(weightKg, unit, withUnit = false),
                    unit = unit.label,
                    onDecrement = {
                        val d = Calc.snap(unit.fromKg(weightKg) - settings.weightIncrement, settings.weightIncrement)
                        weightKg = unit.toKg(d).coerceAtLeast(barKg ?: 0.0)
                    },
                    onIncrement = { weightKg = unit.toKg(Calc.snap(unit.fromKg(weightKg) + settings.weightIncrement, settings.weightIncrement)) },
                    onTapValue = { editing = "weight" },
                    modifier = Modifier.weight(1.25f),
                    decrementEnabled = weightKg > (barKg ?: 0.0) + 0.0001
                )
                Stepper(
                    label = "Reps",
                    value = reps.toString(),
                    onDecrement = { reps = (reps - 1).coerceAtLeast(0) },
                    onIncrement = { reps += 1 },
                    onTapValue = { editing = "reps" },
                    modifier = Modifier.weight(1f),
                    decrementEnabled = reps > 0
                )
            }
            if (barKg != null) {
                Spacer(Modifier.height(8.dp))
                PlateBreakdown(totalKg = weightKg, barKg = barKg!!, unit = unit, compact = true)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = warmup, onClick = { warmup = !warmup }, label = { Text("Warm-up") })
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(12.dp), colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onSave(set.copy(weightKg = weightKg, reps = reps, isWarmup = warmup, barWeightKg = barKg)) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Save", style = MaterialTheme.typography.titleMedium) }
        }
    }

    when (editing) {
        "weight" -> NumberPadSheet(
            title = "Weight",
            initial = unit.fromKg(weightKg),
            unitLabel = unit.label,
            allowDecimal = true,
            onConfirm = { weightKg = unit.toKg(it); editing = null },
            onDismiss = { editing = null },
            bar = barKg?.let { b ->
                BarOptions(unit, b, barChoices(settings.defaultBarWeightKg, b)) { newBar ->
                    val plates = (weightKg - b).coerceAtLeast(0.0)
                    barKg = newBar; weightKg = newBar + plates
                }
            }
        )
        "reps" -> NumberPadSheet(
            title = "Reps",
            initial = reps.toDouble(),
            unitLabel = null,
            allowDecimal = false,
            onConfirm = { reps = it.toInt().coerceIn(0, 999); editing = null },
            onDismiss = { editing = null }
        )
    }
}

fun barChoices(defaultBarKg: Double, current: Double): List<Double> =
    (listOf(defaultBarKg, 20.0, 15.0, 10.0, 7.5, 0.0) + current).distinct().sortedDescending()
