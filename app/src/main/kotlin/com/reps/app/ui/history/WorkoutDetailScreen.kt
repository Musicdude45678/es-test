package com.reps.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.AppContainer
import com.reps.app.data.db.SetEntity
import com.reps.app.data.db.WorkoutExerciseWithSets
import com.reps.app.data.db.WorkoutWithExercises
import com.reps.app.data.prefs.Settings
import com.reps.app.domain.Calc
import com.reps.app.domain.Dates
import com.reps.app.ui.LocalSnackbar
import com.reps.app.ui.appViewModel
import com.reps.app.ui.collectAsStateWithLifecycleCompat
import com.reps.app.ui.components.ConfirmDialog
import com.reps.app.ui.components.StatTile
import com.reps.app.ui.workout.SetEditSheet
import com.reps.app.ui.workout.SetRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkoutDetailState(
    val loaded: Boolean = false,
    val workout: WorkoutWithExercises? = null,
    val prSetIds: Set<String> = emptySet(),
    val settings: Settings = Settings(),
    val activeExists: Boolean = false
)

class WorkoutDetailViewModel(private val container: AppContainer, private val workoutId: String) : ViewModel() {
    private val repo = container.repository
    private val prFlags = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    val state = combine(repo.observeWorkout(workoutId), prFlags, container.settings.settings, repo.observeActiveWorkout()) { w, pr, settings, active ->
        WorkoutDetailState(true, w, pr.filterValues { it }.keys, settings, active != null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailState())

    init {
        viewModelScope.launch {
            repo.observeWorkout(workoutId).collect { w ->
                w?.exercises?.forEach { item ->
                    item.sets.forEach { s ->
                        if (!prFlags.value.containsKey(s.id)) prFlags.update { it + (s.id to repo.isPersonalRecord(item.exercise.id, s)) }
                    }
                }
            }
        }
    }

    fun updateSet(set: SetEntity) = viewModelScope.launch { repo.updateSet(set); prFlags.update { it - set.id } }
    fun deleteSet(set: SetEntity) = viewModelScope.launch { repo.deleteSet(set) }
    fun rename(name: String) = viewModelScope.launch { repo.renameWorkout(workoutId, name) }
    suspend fun delete() = repo.deleteWorkout(workoutId)
    suspend fun repeat(): String? = repo.startFromWorkout(workoutId)
    suspend fun saveAsRoutine(name: String): String? = repo.saveWorkoutAsRoutine(workoutId, name)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    workoutId: String,
    onBack: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    onEditRoutine: (String) -> Unit
) {
    val vm = appViewModel(key = "detail-$workoutId") { WorkoutDetailViewModel(it, workoutId) }
    val state by vm.state.collectAsStateWithLifecycleCompat()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var menu by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showSaveRoutine by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<SetEntity, WorkoutExerciseWithSets>?>(null) }
    val workout = state.workout
    val unit = state.settings.unit

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = { Text(workout?.workout?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (!state.activeExists) DropdownMenuItem(text = { Text("Repeat workout") }, onClick = { menu = false; scope.launch { vm.repeat()?.let(onOpenWorkout) } })
                            DropdownMenuItem(text = { Text("Save as routine") }, onClick = { menu = false; showSaveRoutine = true })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; showRename = true })
                            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; showDelete = true })
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (workout == null) return@Scaffold
        val duration = workout.workout.endedAt?.let { it - workout.workout.startedAt }
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "${Dates.relativeDay(workout.workout.startedAt)} · ${Dates.time(workout.workout.startedAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Duration", duration?.let { Calc.formatDurationShort(it) } ?: "—", Modifier.weight(1f).fillMaxHeight())
                        StatTile("Sets", workout.totalSets.toString(), Modifier.weight(1f).fillMaxHeight())
                        StatTile("Volume", Calc.formatVolume(workout.totalVolumeKg, unit, withUnit = false), Modifier.weight(1f).fillMaxHeight(), sub = unit.label)
                    }
                    if (state.prSetIds.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        StatTile("Personal records", "🏆 ${state.prSetIds.size}", Modifier.fillMaxWidth(), accent = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            items(workout.sortedExercises, key = { it.workoutExercise.id }) { item ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(Modifier.clickable { onOpenExercise(item.exercise.id) }, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${item.exercise.equipment.label} · ${item.exercise.muscleGroup.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            val vol = item.sets.filter { !it.isWarmup }.sumOf { it.weightKg * it.reps }
                            Text(Calc.formatVolume(vol, unit), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        if (item.sets.isEmpty()) {
                            Text("No sets logged", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        var working = 0
                        item.sortedSets.forEach { set ->
                            val label = if (set.isWarmup) "W" else { working++; working.toString() }
                            SetRow(label = label, set = set, unit = unit, isPr = set.id in state.prSetIds, onClick = { editing = set to item })
                        }
                    }
                }
            }
        }
    }

    editing?.let { (set, item) ->
        SetEditSheet(
            set = set, exercise = item.exercise, settings = state.settings,
            onSave = { vm.updateSet(it); editing = null },
            onDelete = { vm.deleteSet(set); editing = null },
            onDismiss = { editing = null }
        )
    }

    if (showDelete) {
        ConfirmDialog(
            title = "Delete workout?",
            body = "All sets from this workout will be removed. This cannot be undone.",
            confirmLabel = "Delete", destructive = true,
            onConfirm = { showDelete = false; scope.launch { vm.delete(); onBack() } },
            onDismiss = { showDelete = false }
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

    if (showSaveRoutine && workout != null) {
        var name by remember { mutableStateOf(workout.workout.name) }
        AlertDialog(
            onDismissRequest = { showSaveRoutine = false },
            title = { Text("Save as routine") },
            text = {
                Column {
                    Text("The exercises from this workout become a routine you can start with one tap.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Routine name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    showSaveRoutine = false
                    scope.launch {
                        val id = vm.saveAsRoutine(name)
                        if (id != null) {
                            val r = snackbar.showSnackbar("Routine saved", actionLabel = "Edit")
                            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) onEditRoutine(id)
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveRoutine = false }) { Text("Cancel") } }
        )
    }
}
