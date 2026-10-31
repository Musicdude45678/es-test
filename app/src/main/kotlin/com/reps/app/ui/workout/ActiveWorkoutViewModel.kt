package com.reps.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.AppContainer
import com.reps.app.data.db.ExerciseEntity
import com.reps.app.data.db.SetEntity
import com.reps.app.data.db.WorkoutExerciseWithSets
import com.reps.app.data.db.WorkoutWithExercises
import com.reps.app.data.prefs.Settings
import com.reps.app.domain.Calc
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the next logged set for an exercise will be. Lives in memory until the user taps "Log set". */
data class PendingSet(val weightKg: Double, val reps: Int, val barKg: Double?, val warmup: Boolean = false)

data class ExerciseUi(
    val item: WorkoutExerciseWithSets,
    val previous: List<SetEntity>,
    val pending: PendingSet,
    val prSetIds: Set<String>
) {
    val exercise: ExerciseEntity get() = item.exercise
}

data class RestTimer(val endsAt: Long, val totalSeconds: Int)

data class WorkoutUiState(
    val loaded: Boolean = false,
    val workout: WorkoutWithExercises? = null,
    val exercises: List<ExerciseUi> = emptyList(),
    val settings: Settings = Settings(),
    val restRemaining: Int? = null,
    val restTotal: Int = 0,
    val lastPrSetId: String? = null
)

class ActiveWorkoutViewModel(private val container: AppContainer, val workoutId: String) : ViewModel() {
    private val repo = container.repository
    private val pending = MutableStateFlow<Map<String, PendingSet>>(emptyMap())
    private val previous = MutableStateFlow<Map<String, List<SetEntity>>>(emptyMap())
    private val prFlags = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val rest = MutableStateFlow<RestTimer?>(null)
    private val restRemaining = MutableStateFlow<Int?>(null)
    private val lastPr = MutableStateFlow<String?>(null)
    private var tickJob: Job? = null

    private val workoutFlow = repo.observeWorkout(workoutId)

    val state: StateFlow<WorkoutUiState> = combine(
        combine(workoutFlow, pending, previous, prFlags) { w, p, prev, pr -> Quad(w, p, prev, pr) },
        container.settings.settings,
        restRemaining,
        rest,
        lastPr
    ) { (w, p, prev, pr), settings, remaining, restTimer, lastPrId ->
        WorkoutUiState(
            loaded = true,
            workout = w,
            exercises = w?.sortedExercises?.map { item ->
                ExerciseUi(
                    item = item,
                    previous = prev[item.exercise.id] ?: emptyList(),
                    pending = p[item.workoutExercise.id] ?: defaultPending(item, prev[item.exercise.id], settings),
                    prSetIds = item.sets.filter { pr[it.id] == true }.map { it.id }.toSet()
                )
            } ?: emptyList(),
            settings = settings,
            restRemaining = remaining,
            restTotal = restTimer?.totalSeconds ?: 0,
            lastPrSetId = lastPrId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    init {
        viewModelScope.launch {
            workoutFlow.collect { w ->
                if (w == null) return@collect
                val settings = container.settings.current()
                // Load "last time" data for exercises we have not seen yet.
                w.exercises.map { it.exercise.id }.distinct().forEach { exId ->
                    if (!previous.value.containsKey(exId)) {
                        val sets = repo.previousSets(exId, workoutId)
                        previous.update { it + (exId to sets) }
                    }
                }
                // Make sure every exercise has a pending set to log.
                pending.update { current ->
                    val next = current.toMutableMap()
                    w.exercises.forEach { item ->
                        if (!next.containsKey(item.workoutExercise.id)) {
                            next[item.workoutExercise.id] = defaultPending(item, previous.value[item.exercise.id], settings)
                        }
                    }
                    next
                }
                // Flag personal records for sets we have not checked.
                w.exercises.forEach { item ->
                    item.sets.forEach { set ->
                        if (!prFlags.value.containsKey(set.id)) {
                            val isPr = repo.isPersonalRecord(item.exercise.id, set)
                            prFlags.update { it + (set.id to isPr) }
                        }
                    }
                }
            }
        }
    }

    fun barKgFor(exercise: ExerciseEntity, settings: Settings): Double? =
        if (!exercise.equipment.usesBar) null else exercise.barWeightKg ?: exercise.equipment.defaultBarKg(settings.defaultBarWeightKg)

    private fun defaultPending(item: WorkoutExerciseWithSets, prev: List<SetEntity>?, settings: Settings): PendingSet {
        val bar = barKgFor(item.exercise, settings)
        val last = item.sortedSets.lastOrNull()
        if (last != null) return PendingSet(last.weightKg, last.reps, if (bar != null) (last.barWeightKg ?: bar) else null, false)
        val working = prev?.filter { !it.isWarmup }.orEmpty()
        if (working.isNotEmpty()) {
            val group = working.groupBy { it.weightKg }.entries.maxWith(compareBy({ it.value.size }, { it.key }))
            val top = group.value.maxOf { it.reps }
            return PendingSet(group.key, top, if (bar != null) (group.value.first().barWeightKg ?: bar) else null, false)
        }
        return PendingSet(weightKg = bar ?: 0.0, reps = if (item.exercise.equipment.usesBar) 5 else 10, barKg = bar)
    }

    private fun updatePending(weId: String, transform: (PendingSet) -> PendingSet) {
        val current = pending.value[weId] ?: state.value.exercises.firstOrNull { it.item.workoutExercise.id == weId }?.pending ?: return
        pending.update { it + (weId to transform(current)) }
    }

    fun setWeight(weId: String, weightKg: Double) = updatePending(weId) { it.copy(weightKg = weightKg.coerceAtLeast(0.0)) }
    fun setReps(weId: String, reps: Int) = updatePending(weId) { it.copy(reps = reps.coerceIn(0, 999)) }
    fun toggleWarmup(weId: String) = updatePending(weId) { it.copy(warmup = !it.warmup) }

    fun setBar(weId: String, barKg: Double) = updatePending(weId) { p ->
        val oldBar = p.barKg ?: 0.0
        // keep the plates the same, move the bar
        val plates = (p.weightKg - oldBar).coerceAtLeast(0.0)
        p.copy(barKg = barKg, weightKg = barKg + plates)
    }

    fun stepWeight(weId: String, direction: Int) {
        val s = state.value.settings
        updatePending(weId) { p ->
            val display = s.unit.fromKg(p.weightKg)
            val stepped = Calc.snap(display + direction * s.weightIncrement, s.weightIncrement)
            val minKg = p.barKg ?: 0.0
            p.copy(weightKg = s.unit.toKg(stepped).coerceAtLeast(minKg))
        }
    }

    fun stepReps(weId: String, direction: Int) = updatePending(weId) { it.copy(reps = (it.reps + direction).coerceIn(0, 999)) }

    fun logSet(weId: String) {
        val ui = state.value.exercises.firstOrNull { it.item.workoutExercise.id == weId } ?: return
        val p = ui.pending
        viewModelScope.launch {
            val set = repo.logSet(weId, p.weightKg, p.reps, p.barKg, p.warmup)
            val isPr = repo.isPersonalRecord(ui.exercise.id, set)
            prFlags.update { it + (set.id to isPr) }
            if (isPr) lastPr.value = set.id
            if (p.warmup) updatePending(weId) { it.copy(warmup = false) }
            val settings = state.value.settings
            if (settings.haptics) container.vibrate(if (isPr) 80 else 30, strong = isPr)
            if (settings.autoRestTimer && !p.warmup) startRest(settings.restTimerSeconds)
        }
    }

    fun updateSet(set: SetEntity) = viewModelScope.launch {
        repo.updateSet(set)
        prFlags.update { it - set.id } // re-evaluate
    }

    fun deleteSet(set: SetEntity) = viewModelScope.launch { repo.deleteSet(set) }

    fun addExercises(ids: List<String>) = viewModelScope.launch { repo.addExercisesToWorkout(workoutId, ids) }
    fun removeExercise(weId: String) = viewModelScope.launch { repo.removeWorkoutExercise(weId) }
    fun moveExercise(from: Int, to: Int) = viewModelScope.launch { state.value.workout?.let { repo.moveWorkoutExercise(it, from, to) } }
    fun rename(name: String) = viewModelScope.launch { repo.renameWorkout(workoutId, name) }

    suspend fun finish() {
        repo.finishWorkout(workoutId)
        stopRest()
        container.sync.pushIfEnabled()
    }

    suspend fun discard() {
        stopRest()
        repo.deleteWorkout(workoutId)
    }

    // ---------- Rest timer ----------
    fun startRest(seconds: Int) {
        rest.value = RestTimer(System.currentTimeMillis() + seconds * 1000L, seconds)
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                val r = rest.value ?: break
                val remaining = ((r.endsAt - System.currentTimeMillis()) / 1000.0).let { kotlin.math.ceil(it).toInt() }
                if (remaining <= 0) {
                    restRemaining.value = null
                    rest.value = null
                    if (state.value.settings.haptics) container.vibratePattern(longArrayOf(0, 250, 120, 250, 120, 450))
                    break
                }
                restRemaining.value = remaining
                delay(250)
            }
        }
    }

    fun addRest(seconds: Int) {
        rest.update { r -> r?.copy(endsAt = r.endsAt + seconds * 1000L, totalSeconds = r.totalSeconds + seconds) }
    }

    fun stopRest() {
        tickJob?.cancel()
        rest.value = null
        restRemaining.value = null
    }

    fun clearLastPr() { lastPr.value = null }

    suspend fun currentSettings(): Settings = container.settings.settings.first()
}
