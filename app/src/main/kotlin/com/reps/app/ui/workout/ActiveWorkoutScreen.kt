package com.reps.app.ui.workout

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reps.app.data.db.SetEntity
import com.reps.app.data.prefs.Settings
import com.reps.app.domain.Calc
import com.reps.app.ui.LocalSnackbar
import com.reps.app.ui.appViewModel
import com.reps.app.ui.collectAsStateWithLifecycleCompat
import com.reps.app.ui.components.BarOptions
import com.reps.app.ui.components.ConfirmDialog
import com.reps.app.ui.components.EmptyState
import com.reps.app.ui.components.NumberPadSheet
import com.reps.app.ui.components.PlateBreakdown
import com.reps.app.ui.components.Stepper
import com.reps.app.ui.picker.ExercisePickerSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    workoutId: String,
    onBack: () -> Unit,
    onFinished: (String) -> Unit,
    onDiscarded: () -> Unit,
    onOpenExercise: (String) -> Unit
) {
    val vm = appViewModel(key = "workout-$workoutId") { ActiveWorkoutViewModel(it, workoutId) }
    val state by vm.state.collectAsStateWithLifecycleCompat()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current

    var showPicker by remember { mutableStateOf(false) }
    var showFinish by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<Pair<SetEntity, ExerciseUi>?>(null) }
    var numberPad by remember { mutableStateOf<Pair<String, String>?>(null) } // (workoutExerciseId, "weight"|"reps")

    // Keep the screen on while training.
    val keepOn = state.settings.keepScreenOn
    DisposableEffect(keepOn) {
        val window = (context as? android.app.Activity)?.window
        if (keepOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Celebrate PRs.
    LaunchedEffect(state.lastPrSetId) {
        val id = state.lastPrSetId ?: return@LaunchedEffect
        val set = state.exercises.flatMap { ex -> ex.item.sets.map { ex to it } }.firstOrNull { it.second.id == id } ?: return@LaunchedEffect
        snackbar.showSnackbar("New record: ${set.first.exercise.name} ${Calc.formatWeight(set.second.weightKg, state.settings.unit)} × ${set.second.reps}")
        vm.clearLastPr()
    }

    BackHandler { onBack() }

    val workout = state.workout
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimise") } },
                title = {
                    Column(Modifier.clickable { showRename = true }) {
                        Text(workout?.workout?.name ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        workout?.let { ElapsedText(it.workout.startedAt) }
                    }
                },
                actions = {
                    Button(
                        onClick = { if ((workout?.totalSets ?: 0) == 0 && workout?.exercises?.all { it.sets.isEmpty() } == true) showDiscard = true else showFinish = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) { Text("Finish") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename workout") }, onClick = { menu = false; showRename = true })
                            DropdownMenuItem(text = { Text("Start rest timer") }, leadingIcon = { Icon(Icons.Outlined.Timer, null) }, onClick = { menu = false; vm.startRest(state.settings.restTimerSeconds) })
                            DropdownMenuItem(text = { Text("Discard workout", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; showDiscard = true })
                        }
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = state.restRemaining != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                RestTimerBar(
                    remaining = state.restRemaining ?: 0,
                    total = state.restTotal,
                    onAdd = { vm.addRest(30) },
                    onSkip = { vm.stopRest() }
                )
            }
        }
    ) { padding ->
        if (workout == null) {
            if (state.loaded) LaunchedEffect(Unit) { onDiscarded() }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.exercises.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Add,
                        title = "Add your first exercise",
                        body = "Pick what you're lifting and start logging sets. Weight and reps are pre-filled from last time."
                    )
                }
            }
            items(state.exercises, key = { it.item.workoutExercise.id }) { ex ->
                ExerciseCard(
                    ui = ex,
                    settings = state.settings,
                    index = state.exercises.indexOf(ex),
                    count = state.exercises.size,
                    onLog = { vm.logSet(ex.item.workoutExercise.id) },
                    onStepWeight = { vm.stepWeight(ex.item.workoutExercise.id, it) },
                    onStepReps = { vm.stepReps(ex.item.workoutExercise.id, it) },
                    onTapWeight = { numberPad = ex.item.workoutExercise.id to "weight" },
                    onTapReps = { numberPad = ex.item.workoutExercise.id to "reps" },
                    onToggleWarmup = { vm.toggleWarmup(ex.item.workoutExercise.id) },
                    onEditSet = { editingSet = it to ex },
                    onRemove = { vm.removeExercise(ex.item.workoutExercise.id) },
                    onMove = { dir -> vm.moveExercise(state.exercises.indexOf(ex), state.exercises.indexOf(ex) + dir) },
                    onOpenHistory = { onOpenExercise(ex.exercise.id) }
                )
            }
            item {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add exercise", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            onDismiss = { showPicker = false },
            onPicked = { ids -> vm.addExercises(ids); showPicker = false }
        )
    }

    numberPad?.let { (weId, field) ->
        val ex = state.exercises.firstOrNull { it.item.workoutExercise.id == weId }
        if (ex == null) { numberPad = null } else {
            val unit = state.settings.unit
            if (field == "weight") {
                NumberPadSheet(
                    title = "${ex.exercise.name} · weight",
                    initial = unit.fromKg(ex.pending.weightKg),
                    unitLabel = unit.label,
                    allowDecimal = true,
                    onConfirm = { vm.setWeight(weId, unit.toKg(it)); numberPad = null },
                    onDismiss = { numberPad = null },
                    bar = ex.pending.barKg?.let { b -> BarOptions(unit, b, barChoices(state.settings.defaultBarWeightKg, b)) { vm.setBar(weId, it) } }
                )
            } else {
                NumberPadSheet(
                    title = "${ex.exercise.name} · reps",
                    initial = ex.pending.reps.toDouble(),
                    unitLabel = null,
                    allowDecimal = false,
                    onConfirm = { vm.setReps(weId, it.toInt()); numberPad = null },
                    onDismiss = { numberPad = null },
                    quickValues = listOf(5.0, 8.0, 10.0, 12.0, 15.0)
                )
            }
        }
    }

    editingSet?.let { (set, ex) ->
        SetEditSheet(
            set = set,
            exercise = ex.exercise,
            settings = state.settings,
            onSave = { vm.updateSet(it); editingSet = null },
            onDelete = { vm.deleteSet(set); editingSet = null },
            onDismiss = { editingSet = null }
        )
    }

    if (showFinish && workout != null) {
        val unit = state.settings.unit
        AlertDialog(
            onDismissRequest = { showFinish = false },
            icon = { Icon(Icons.Default.Check, contentDescription = null) },
            title = { Text("Finish workout?") },
            text = {
                Column {
                    Text("${Calc.formatDurationShort(System.currentTimeMillis() - workout.workout.startedAt)} · ${workout.totalSets} sets · ${Calc.formatVolume(workout.totalVolumeKg, unit)}", style = MaterialTheme.typography.bodyMedium)
                    val prs = state.exercises.sumOf { it.prSetIds.size }
                    if (prs > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text("🏆 $prs new personal record${if (prs > 1) "s" else ""}", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showFinish = false; scope.launch { vm.finish(); onFinished(workoutId) } }) { Text("Finish") }
            },
            dismissButton = { TextButton(onClick = { showFinish = false }) { Text("Keep going") } }
        )
    }

    if (showDiscard) {
        ConfirmDialog(
            title = "Discard workout?",
            body = "Nothing from this workout will be saved.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = { showDiscard = false; scope.launch { vm.discard(); onDiscarded() } },
            onDismiss = { showDiscard = false }
        )
    }

    if (showRename && workout != null) {
        var name by remember { mutableStateOf(workout.workout.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Workout name") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.rename(name); showRename = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ElapsedText(startedAt: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    Text(Calc.formatDuration(now - startedAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun RestTimerBar(remaining: Int, total: Int, onAdd: () -> Unit, onSkip: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Column(Modifier.padding(bottom = 8.dp)) {
            LinearProgressIndicator(
                progress = { if (total > 0) remaining / total.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Rest", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Calc.formatDuration(remaining * 1000L), fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                }
                FilledTonalButton(onClick = onAdd, shape = RoundedCornerShape(12.dp)) { Text("+30s") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onSkip) { Text("Skip") }
            }
        }
    }
}

@Composable
internal fun ExerciseCard(
    ui: ExerciseUi,
    settings: Settings,
    index: Int,
    count: Int,
    onLog: () -> Unit,
    onStepWeight: (Int) -> Unit,
    onStepReps: (Int) -> Unit,
    onTapWeight: () -> Unit,
    onTapReps: () -> Unit,
    onToggleWarmup: () -> Unit,
    onEditSet: (SetEntity) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onOpenHistory: () -> Unit
) {
    val unit = settings.unit
    var menu by remember { mutableStateOf(false) }
    val exercise = ui.exercise
    val sets = ui.item.sortedSets
    val bar = ui.pending.barKg

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick = onOpenHistory)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${exercise.equipment.label} · ${exercise.muscleGroup.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Exercise options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("History & records") }, onClick = { menu = false; onOpenHistory() })
                        if (index > 0) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMove(-1) })
                        if (index < count - 1) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMove(1) })
                        DropdownMenuItem(text = { Text("Remove from workout", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onRemove() })
                    }
                }
            }

            if (ui.previous.isNotEmpty()) {
                val working = ui.previous.filter { !it.isWarmup }.ifEmpty { ui.previous }
                val summary = working.groupBy { it.weightKg }.entries.joinToString("  ·  ") { (w, list) ->
                    Calc.formatWeight(w, unit) + " × " + list.joinToString(", ") { it.reps.toString() }
                }
                Text(
                    "Last time  $summary",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, end = 10.dp)
                )
            }

            if (sets.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 10.dp)) {
                    var workingIndex = 0
                    sets.forEach { set ->
                        val label = if (set.isWarmup) "W" else { workingIndex++; workingIndex.toString() }
                        SetRow(label = label, set = set, unit = unit, isPr = set.id in ui.prSetIds, onClick = { onEditSet(set) })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(end = 10.dp))
            Spacer(Modifier.height(12.dp))

            Row(Modifier.padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stepper(
                    label = if (exercise.equipment.perHand) "Per hand" else if (exercise.equipment == com.reps.app.data.model.Equipment.BODYWEIGHT) "Added" else "Weight",
                    value = Calc.formatWeight(ui.pending.weightKg, unit, withUnit = false),
                    unit = unit.label,
                    onDecrement = { onStepWeight(-1) },
                    onIncrement = { onStepWeight(1) },
                    onTapValue = onTapWeight,
                    modifier = Modifier.weight(1.25f),
                    decrementEnabled = ui.pending.weightKg > (bar ?: 0.0) + 0.0001
                )
                Stepper(
                    label = "Reps",
                    value = ui.pending.reps.toString(),
                    onDecrement = { onStepReps(-1) },
                    onIncrement = { onStepReps(1) },
                    onTapValue = onTapReps,
                    modifier = Modifier.weight(1f),
                    decrementEnabled = ui.pending.reps > 0
                )
            }
            if (bar != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 10.dp)) {
                    Text("Bar ${Calc.formatWeight(bar, unit)}  ·  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    PlateBreakdown(totalKg = ui.pending.weightKg, barKg = bar, unit = unit, compact = true, modifier = Modifier.weight(1f, fill = false))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = ui.pending.warmup,
                    onClick = onToggleWarmup,
                    label = { Text("Warm-up") },
                    modifier = Modifier.height(48.dp)
                )
                Button(
                    onClick = onLog,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log set", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                }
            }
        }
    }
}

@Composable
fun SetRow(label: String, set: SetEntity, unit: com.reps.app.data.model.WeightUnit, isPr: Boolean, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 44.dp)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (set.isWarmup) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (set.isWarmup) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Text(Calc.formatWeight(set.weightKg, unit), style = MaterialTheme.typography.titleMedium)
        Text("  ×  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${set.reps}", style = MaterialTheme.typography.titleMedium)
        Text(" reps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        if (isPr) {
            Icon(Icons.Default.EmojiEvents, contentDescription = "Personal record", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
        }
    }
}
