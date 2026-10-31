package com.reps.app.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.AppContainer
import com.reps.app.data.db.ExerciseEntity
import com.reps.app.data.db.SetRecord
import com.reps.app.data.prefs.Settings
import com.reps.app.domain.Calc
import com.reps.app.domain.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WeekVolume(val startMillis: Long, val volumeKg: Double, val workouts: Int)

data class PrEvent(val exercise: ExerciseEntity, val weightKg: Double, val reps: Int, val at: Long, val workoutId: String)

data class ExerciseSummary(
    val exercise: ExerciseEntity,
    val bestWeightKg: Double,
    val best1RmKg: Double,
    val lastPerformed: Long,
    val sessions: Int,
    val trend: List<Float>
)

data class ProgressState(
    val loaded: Boolean = false,
    val hasData: Boolean = false,
    val weeks: List<WeekVolume> = emptyList(),
    val recentPrs: List<PrEvent> = emptyList(),
    val exercises: List<ExerciseSummary> = emptyList(),
    val query: String = "",
    val totalWorkouts: Int = 0,
    val totalSets: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val settings: Settings = Settings()
)

class ProgressViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val query = MutableStateFlow("")

    val state = combine(repo.observeAllRecords(), repo.observeAllExercises(), container.settings.settings, query) { records, exercises, settings, q ->
        val byExercise = exercises.associateBy { it.id }
        val working = records.filter { !it.set.isWarmup }

        // Weekly volume, last 8 weeks (oldest first).
        val weeks = (7 downTo 0).map { ago ->
            val start = Dates.startOfWeekMillis(ago)
            val end = start + 7L * 24 * 3600 * 1000
            val inWeek = working.filter { it.workoutStartedAt in start until end }
            WeekVolume(start, inWeek.sumOf { it.set.weightKg * it.set.reps }, inWeek.map { it.workoutId }.distinct().size)
        }

        // PR events: a working set heavier than everything before it for that exercise.
        val prs = mutableListOf<PrEvent>()
        working.groupBy { it.exerciseId }.forEach { (exId, list) ->
            val ex = byExercise[exId] ?: return@forEach
            var best = 0.0
            list.sortedWith(compareBy({ it.set.completedAt }, { it.set.position })).forEach { r ->
                if (r.set.weightKg > best + 0.0001 && r.set.reps > 0) {
                    best = r.set.weightKg
                    prs += PrEvent(ex, r.set.weightKg, r.set.reps, r.set.completedAt, r.workoutId)
                }
            }
        }

        val summaries = working.groupBy { it.exerciseId }.mapNotNull { (exId, list) ->
            val ex = byExercise[exId] ?: return@mapNotNull null
            val sessions = list.groupBy { it.workoutId }.values.sortedBy { it.first().workoutStartedAt }
            ExerciseSummary(
                exercise = ex,
                bestWeightKg = list.maxOf { it.set.weightKg },
                best1RmKg = list.maxOf { Calc.estimate1Rm(it.set.weightKg, it.set.reps) },
                lastPerformed = list.maxOf { it.workoutStartedAt },
                sessions = sessions.size,
                trend = sessions.takeLast(10).map { s -> s.maxOf { it.set.weightKg }.toFloat() }
            )
        }.sortedByDescending { it.lastPerformed }

        val needle = q.trim().lowercase()
        ProgressState(
            loaded = true,
            hasData = records.isNotEmpty(),
            weeks = weeks,
            recentPrs = prs.sortedByDescending { it.at }.take(5),
            exercises = if (needle.isEmpty()) summaries else summaries.filter { it.exercise.name.lowercase().contains(needle) },
            query = q,
            totalWorkouts = records.map { it.workoutId }.distinct().size,
            totalSets = working.size,
            totalVolumeKg = working.sumOf { it.set.weightKg * it.set.reps },
            settings = settings
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState())

    fun setQuery(q: String) { query.value = q }
}
