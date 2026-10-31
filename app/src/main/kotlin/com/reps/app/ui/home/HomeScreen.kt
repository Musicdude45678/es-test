package com.reps.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.reps.app.data.db.RoutineWithExercises
import com.reps.app.data.db.WorkoutSummary
import com.reps.app.data.model.WeightUnit
import com.reps.app.domain.Calc
import com.reps.app.domain.Dates
import com.reps.app.ui.appViewModel
import com.reps.app.ui.collectAsStateWithLifecycleCompat
import com.reps.app.ui.components.ConfirmDialog
import com.reps.app.ui.components.SectionHeader
import com.reps.app.ui.navigation.RepsBottomBar
import com.reps.app.ui.navigation.Routes
import com.reps.app.ui.navigateTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    nav: NavHostController,
    onOpenWorkout: (String) -> Unit,
    onOpenWorkoutDetail: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onEditRoutine: (String?) -> Unit
) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycleCompat()
    val scope = rememberCoroutineScope()
    var routineToDelete by remember { mutableStateOf<RoutineWithExercises?>(null) }

    Scaffold(bottomBar = { RepsBottomBar(nav, Routes.HOME) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Dates.greeting(), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                }
            }

            item {
                val active = state.active
                if (active != null) {
                    ActiveWorkoutCard(
                        name = active.workout.name,
                        startedAt = active.workout.startedAt,
                        sets = active.totalSets,
                        exercises = active.exercises.size,
                        onClick = { onOpenWorkout(active.workout.id) }
                    )
                } else {
                    Button(
                        onClick = { scope.launch { onOpenWorkout(vm.startEmpty()) } },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(64.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Start workout", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            item { WeekCard(state) }

            item {
                SectionHeader("Routines") {
                    TextButton(onClick = { onEditRoutine(null) }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("New")
                    }
                }
            }
            if (state.routines.isEmpty() && state.loaded) {
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { onEditRoutine(null) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("No routines yet", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Save a workout as a routine to start it again with one tap.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            items(state.routines, key = { it.routine.id }) { routine ->
                RoutineRow(
                    routine = routine,
                    enabled = state.active == null,
                    onStart = { scope.launch { onOpenWorkout(vm.startRoutine(routine)) } },
                    onEdit = { onEditRoutine(routine.routine.id) },
                    onDelete = { routineToDelete = routine }
                )
            }

            if (state.recent.isNotEmpty()) {
                item {
                    SectionHeader("Recent", modifier = Modifier.padding(top = 8.dp)) {
                        TextButton(onClick = { nav.navigateTab(Routes.HISTORY) }) { Text("See all") }
                    }
                }
                items(state.recent, key = { it.id }) { w ->
                    WorkoutRow(
                        workout = w,
                        unit = state.settings.unit,
                        onClick = { onOpenWorkoutDetail(w.id) },
                        trailing = {
                            if (state.active == null) {
                                IconButton(onClick = { scope.launch { vm.repeatWorkout(w.id)?.let(onOpenWorkout) } }) {
                                    Icon(Icons.Default.Replay, contentDescription = "Repeat workout", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    routineToDelete?.let { r ->
        ConfirmDialog(
            title = "Delete routine?",
            body = "\"${r.routine.name}\" will be removed. Your logged workouts are kept.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { scope.launch { vm.deleteRoutine(r.routine.id) }; routineToDelete = null },
            onDismiss = { routineToDelete = null }
        )
    }
}

@Composable
private fun ActiveWorkoutCard(name: String, startedAt: Long, sets: Int, exercises: Int, onClick: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("IN PROGRESS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                Text(
                    "${Calc.formatDuration(now - startedAt)} · $exercises exercises · $sets sets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}

@Composable
private fun WeekCard(state: HomeUiState) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("This week", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.streakWeeks > 1) {
                    Text(
                        "🔥 ${state.streakWeeks} week streak",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            val today = LocalDate.now().dayOfWeek
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DayOfWeek.values().forEach { day ->
                    val trained = day in state.trainedDays
                    val isToday = day == today
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        trained -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.surfaceContainerHighest
                                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                Dates.dayOfWeekShort(LocalDate.now().with(day)),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (trained) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isToday) {
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row {
                MiniStat("Workouts", state.weekWorkouts.toString(), Modifier.weight(1f))
                MiniStat("Sets", state.weekSets.toString(), Modifier.weight(1f))
                MiniStat("Volume", Calc.formatVolume(state.weekVolumeKg, state.settings.unit), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RoutineRow(routine: RoutineWithExercises, enabled: Boolean, onStart: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable(onClick = onEdit),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(routine.routine.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val names = routine.sortedExercises.map { it.exercise.name }
                Text(
                    names.take(3).joinToString(", ") + if (names.size > 3) " +${names.size - 3}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            FilledTonalButton(onClick = onStart, enabled = enabled, shape = RoundedCornerShape(12.dp)) { Text("Start") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun WorkoutRow(workout: WorkoutSummary, unit: WeightUnit, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(workout.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.size(8.dp))
                    Text(Dates.relativeDay(workout.startedAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(2.dp))
                val duration = workout.endedAt?.let { Calc.formatDurationShort(it - workout.startedAt) }
                Text(
                    listOfNotNull(duration, "${workout.exerciseCount} exercises", "${workout.setCount} sets", Calc.formatVolume(workout.volumeKg, unit)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (trailing != null) trailing() else Spacer(Modifier.size(10.dp))
        }
    }
}
