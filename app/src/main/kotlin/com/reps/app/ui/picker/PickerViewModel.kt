package com.reps.app.ui.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.AppContainer
import com.reps.app.data.db.ExerciseEntity
import com.reps.app.data.model.Equipment
import com.reps.app.data.model.MuscleGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class PickerState(
    val query: String = "",
    val filter: MuscleGroup? = null,
    val all: List<ExerciseEntity> = emptyList(),
    val recent: List<ExerciseEntity> = emptyList(),
    val filtered: List<ExerciseEntity> = emptyList(),
    val selected: Set<String> = emptySet(),
    val selectionOrder: List<String> = emptyList()
) {
    fun orderedSelection(): List<String> = selectionOrder.filter { it in selected }
}

class PickerViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<MuscleGroup?>(null)
    private val selected = MutableStateFlow<List<String>>(emptyList())

    val state: StateFlow<PickerState> = combine(repo.observeExercises(), repo.observeRecentExerciseIds(), query, filter, selected) { all, recentIds, q, f, sel ->
        val byId = all.associateBy { it.id }
        val needle = q.trim().lowercase()
        val filtered = all.filter { ex ->
            (f == null || ex.muscleGroup == f) && (needle.isEmpty() || ex.name.lowercase().contains(needle) || ex.equipment.label.lowercase().contains(needle))
        }.sortedWith(compareBy({ needle.isNotEmpty() && !it.name.lowercase().startsWith(needle) }, { it.name.lowercase() }))
        PickerState(
            query = q,
            filter = f,
            all = all,
            recent = recentIds.mapNotNull { byId[it] },
            filtered = filtered,
            selected = sel.toSet(),
            selectionOrder = sel
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerState())

    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: MuscleGroup?) { filter.value = f }
    fun toggle(id: String) { selected.update { if (id in it) it - id else it + id } }
    fun reset() { selected.value = emptyList(); query.value = ""; filter.value = null }

    suspend fun create(name: String, equipment: Equipment, muscle: MuscleGroup): ExerciseEntity = repo.createExercise(name, equipment, muscle)
}
