package com.reps.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.AppContainer
import com.reps.app.data.db.RoutineWithExercises
import com.reps.app.data.db.WorkoutSummary
import com.reps.app.data.db.WorkoutWithExercises
import com.reps.app.data.prefs.Settings
import com.reps.app.domain.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate

data class HomeUiState(
    val loaded: Boolean = false,
    val active: WorkoutWithExercises? = null,
    val routines: List<RoutineWithExercises> = emptyList(),
    val recent: List<WorkoutSummary> = emptyList(),
    val totalWorkouts: Int = 0,
    val trainedDays: Set<DayOfWeek> = emptySet(),
    val weekWorkouts: Int = 0,
    val weekSets: Int = 0,
    val weekVolumeKg: Double = 0.0,
    val streakWeeks: Int = 0,
    val settings: Settings = Settings()
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository

    val state: StateFlow<HomeUiState> = combine(
        repo.observeActiveWorkout(),
        repo.observeHistory(),
        repo.observeRoutines(),
        container.settings.settings
    ) { active, history, routines, settings ->
        val weekStart = Dates.startOfWeekMillis()
        val thisWeek = history.filter { it.startedAt >= weekStart }
        HomeUiState(
            loaded = true,
            active = active,
            routines = routines,
            recent = history.take(3),
            totalWorkouts = history.size,
            trainedDays = thisWeek.map { Dates.toLocalDate(it.startedAt).dayOfWeek }.toSet(),
            weekWorkouts = thisWeek.size,
            weekSets = thisWeek.sumOf { it.setCount },
            weekVolumeKg = thisWeek.sumOf { it.volumeKg },
            streakWeeks = streak(history),
            settings = settings
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun streak(history: List<WorkoutSummary>): Int {
        if (history.isEmpty()) return 0
        val weeks = history.map { Dates.toLocalDate(it.startedAt).with(DayOfWeek.MONDAY) }.toSet()
        var week = LocalDate.now().with(DayOfWeek.MONDAY)
        var count = 0
        if (week !in weeks) week = week.minusWeeks(1) // the current week still counts if it has not started yet
        while (week in weeks) { count++; week = week.minusWeeks(1) }
        return count
    }

    suspend fun startEmpty(): String = repo.startEmptyWorkout()
    suspend fun startRoutine(routine: RoutineWithExercises): String = repo.startFromRoutine(routine)
    suspend fun repeatWorkout(id: String): String? = repo.startFromWorkout(id)
    suspend fun deleteRoutine(id: String) = repo.deleteRoutine(id)
}
