package com.reps.app.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.reps.app.data.db.ExerciseEntity
import com.reps.app.ui.appViewModel
import com.reps.app.ui.collectAsStateWithLifecycleCompat
import com.reps.app.ui.components.ConfirmDialog
import com.reps.app.ui.components.EmptyState
import com.reps.app.ui.picker.ExercisePickerSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutineEditorState(val loaded: Boolean = false, val name: String = "", val exercises: List<ExerciseEntity> = emptyList(), val isNew: Boolean = true)

class RoutineEditorViewModel(private val container: AppContainer, private val routineId: String?) : ViewModel() {
    private val repo = container.repository
    val state = MutableStateFlow(RoutineEditorState(isNew = routineId == null))

    init {
        viewModelScope.launch {
            if (routineId != null) {
                repo.getRoutine(routineId)?.let { r -> state.update { it.copy(name = r.routine.name, exercises = r.sortedExercises.map { e -> e.exercise }) } }
            }
            state.update { it.copy(loaded = true) }
        }
    }

    fun setName(n: String) = state.update { it.copy(name = n) }
    fun add(ids: List<String>) = viewModelScope.launch {
        val added = ids.mapNotNull { repo.getExercise(it) }
        state.update { it.copy(exercises = it.exercises + added) }
    }
    fun remove(index: Int) = state.update { it.copy(exercises = it.exercises.filterIndexed { i, _ -> i != index }) }
    fun move(from: Int, to: Int) = state.update {
        if (to !in it.exercises.indices) it else {
            val list = it.exercises.toMutableList(); val item = list.removeAt(from); list.add(to, item); it.copy(exercises = list)
        }
    }
    suspend fun save(): String = repo.saveRoutine(routineId, state.value.name.ifBlank { "Routine" }, state.value.exercises.map { it.id })
    suspend fun delete() { routineId?.let { repo.deleteRoutine(it) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(routineId: String?, onBack: () -> Unit) {
    val vm = appViewModel(key = "routine-${routineId ?: "new"}") { RoutineEditorViewModel(it, routineId) }
    val state by vm.state.collectAsStateWithLifecycleCompat()
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = { Text(if (state.isNew) "New routine" else "Edit routine") },
                actions = {
                    TextButton(onClick = { scope.launch { vm.save(); onBack() } }, enabled = state.exercises.isNotEmpty()) { Text("Save") }
                    if (!state.isNew) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Delete routine", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; showDelete = true })
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::setName,
                    label = { Text("Routine name") },
                    placeholder = { Text("e.g. Push day") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(8.dp))
            }
            if (state.exercises.isEmpty()) {
                item { EmptyState(Icons.Default.Add, "No exercises yet", "Add the exercises you want in this routine, in the order you do them.") }
            }
            itemsIndexed(state.exercises, key = { i, e -> "${e.id}-$i" }) { index, ex ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ex.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${ex.equipment.label} · ${ex.muscleGroup.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { vm.move(index, index - 1) }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up") }
                        IconButton(onClick = { vm.move(index, index + 1) }, enabled = index < state.exercises.size - 1) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down") }
                        IconButton(onClick = { vm.remove(index) }) { Icon(Icons.Default.Close, contentDescription = "Remove") }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add exercises")
                }
            }
        }
    }

    if (showPicker) {
        ExercisePickerSheet(onDismiss = { showPicker = false }, onPicked = { vm.add(it); showPicker = false })
    }
    if (showDelete) {
        ConfirmDialog(
            title = "Delete routine?", body = "Your logged workouts are kept.", confirmLabel = "Delete", destructive = true,
            onConfirm = { showDelete = false; scope.launch { vm.delete(); onBack() } },
            onDismiss = { showDelete = false }
        )
    }
}
