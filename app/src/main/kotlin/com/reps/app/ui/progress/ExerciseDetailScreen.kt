package com.reps.app.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.AppContainer
import com.reps.app.data.db.ExerciseEntity
import com.reps.app.data.db.SetEntity
import com.reps.app.data.prefs.Settings
import com.reps.app.domain.Calc
import com.reps.app.domain.Dates
import com.reps.app.ui.appViewModel
import com.reps.app.ui.collectAsStateWithLifecycleCompat
import com.reps.app.ui.components.ChartPlaceholder
import com.reps.app.ui.components.LineChart
import com.reps.app.ui.components.StatTile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class Metric(val label: String) { TOP_WEIGHT("Top weight"), ONE_RM("Est. 1RM"), VOLUME("Volume") }

data class Session(val workoutId: String, val at: Long, val sets: List<SetEntity>, val topWeightKg: Double, val est1RmKg: Double, val volumeKg: Double, val isPr: Boolean)

data class ExerciseDetailState(
    val loaded: Boolean = false,
    val exercise: ExerciseEntity? = null,
    val sessions: List<Session> = emptyList(),
    val bestWeightKg: Double = 0.0,
    val bestWeightReps: Int = 0,
    val best1RmKg: Double = 0.0,
    val bestVolumeSetKg: Double = 0.0,
    val totalReps: Int = 0,
    val metric: Metric = Metric.TOP_WEIGHT,
    val settings: Settings = Settings()
)

class ExerciseDetailViewModel(container: AppContainer, exerciseId: String) : ViewModel() {
    private val repo = container.repository
    private val metric = MutableStateFlow(Metric.TOP_WEIGHT)

    val state = combine(repo.observeExercise(exerciseId), repo.observeExerciseRecords(exerciseId), container.settings.settings, metric) { ex, records, settings, m ->
        val working = records.filter { !it.set.isWarmup }
        var best = 0.0
        val sessions = records.groupBy { it.workoutId }.values.sortedBy { it.first().workoutStartedAt }.map { list ->
            val w = list.filter { !it.set.isWarmup }.ifEmpty { list }
            val top = w.maxOf { it.set.weightKg }
            val pr = top > best + 0.0001
            if (pr) best = top
            Session(
                workoutId = list.first().workoutId,
                at = list.first().workoutStartedAt,
                sets = list.map { it.set }.sortedBy { it.position },
                topWeightKg = top,
                est1RmKg = w.maxOf { Calc.estimate1Rm(it.set.weightKg, it.set.reps) },
                volumeKg = w.sumOf { it.set.weightKg * it.set.reps },
                isPr = pr
            )
        }
        val bestSet = working.maxByOrNull { it.set.weightKg }
        ExerciseDetailState(
            loaded = true,
            exercise = ex,
            sessions = sessions,
            bestWeightKg = bestSet?.set?.weightKg ?: 0.0,
            bestWeightReps = working.filter { it.set.weightKg == bestSet?.set?.weightKg }.maxOfOrNull { it.set.reps } ?: 0,
            best1RmKg = working.maxOfOrNull { Calc.estimate1Rm(it.set.weightKg, it.set.reps) } ?: 0.0,
            bestVolumeSetKg = working.maxOfOrNull { it.set.weightKg * it.set.reps } ?: 0.0,
            totalReps = working.sumOf { it.set.reps },
            metric = m,
            settings = settings
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailState())

    fun setMetric(m: Metric) { metric.value = m }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(exerciseId: String, onBack: () -> Unit, onEdit: () -> Unit, onOpenWorkoutDetail: (String) -> Unit) {
    val vm = appViewModel(key = "exercise-$exerciseId") { ExerciseDetailViewModel(it, exerciseId) }
    val state by vm.state.collectAsStateWithLifecycleCompat()
    val unit = state.settings.unit
    val ex = state.exercise

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = {
                    Column {
                        Text(ex?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (ex != null) Text("${ex.equipment.label} · ${ex.muscleGroup.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit exercise") } }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Best weight", if (state.bestWeightKg > 0) Calc.formatWeight(state.bestWeightKg, unit) else "—", Modifier.weight(1f), sub = if (state.bestWeightReps > 0) "× ${state.bestWeightReps} reps" else null, accent = MaterialTheme.colorScheme.primary)
                        StatTile("Est. 1RM", if (state.best1RmKg > 0) Calc.formatWeight(state.best1RmKg, unit) else "—", Modifier.weight(1f), sub = "Epley formula")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Sessions", state.sessions.size.toString(), Modifier.weight(1f))
                        StatTile("Total reps", state.totalReps.toString(), Modifier.weight(1f))
                    }
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(Metric.values().toList()) { m ->
                                FilterChip(selected = state.metric == m, onClick = { vm.setMetric(m) }, label = { Text(m.label) })
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        val values = state.sessions.map {
                            when (state.metric) {
                                Metric.TOP_WEIGHT -> unit.fromKg(it.topWeightKg).toFloat()
                                Metric.ONE_RM -> unit.fromKg(it.est1RmKg).toFloat()
                                Metric.VOLUME -> unit.fromKg(it.volumeKg).toFloat()
                            }
                        }
                        if (values.size < 2) {
                            ChartPlaceholder(if (values.isEmpty()) "Log this exercise to start a trend line." else "One session so far. Do it again to see a trend.")
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(Dates.shortDate(state.sessions.first().at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${Calc.formatNumber(values.max().toDouble())} ${unit.label} peak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Dates.shortDate(state.sessions.last().at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(6.dp))
                            LineChart(
                                values = values,
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                highlightIndices = if (state.metric == Metric.TOP_WEIGHT) state.sessions.withIndex().filter { it.value.isPr }.map { it.index }.toSet() else emptySet()
                            )
                        }
                    }
                }
            }

            if (state.sessions.isNotEmpty()) {
                item { Text("History", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            }
            items(state.sessions.reversed(), key = { it.workoutId }) { s ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { onOpenWorkoutDetail(s.workoutId) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(Dates.relativeDay(s.at), style = MaterialTheme.typography.titleSmall)
                                if (s.isPr) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Default.EmojiEvents, contentDescription = "Record", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(
                                s.sets.joinToString("  ") { (if (it.isWarmup) "w" else "") + Calc.formatWeight(it.weightKg, unit, withUnit = false) + "×" + it.reps },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(Calc.formatVolume(s.volumeKg, unit), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
