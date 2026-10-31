package com.reps.app.ui.picker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reps.app.data.db.ExerciseEntity
import com.reps.app.data.model.Equipment
import com.reps.app.data.model.MuscleGroup
import com.reps.app.ui.appViewModel
import com.reps.app.ui.collectAsStateWithLifecycleCompat
import kotlinx.coroutines.launch

/**
 * Full-height sheet to pick one or more exercises. Shows recently used exercises first,
 * supports search and muscle-group filtering, and lets the user create a new exercise inline.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExercisePickerSheet(
    onDismiss: () -> Unit,
    onPicked: (List<String>) -> Unit,
    title: String = "Add exercises"
) {
    val vm = appViewModel(key = "picker") { PickerViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycleCompat()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var createName by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = { vm.reset(); onDismiss() }, sheetState = sheetState) {
        Column(Modifier.fillMaxHeight(0.96f)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { createName = state.query.trim() }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = { Text("Search exercises") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = state.filter == null, onClick = { vm.setFilter(null) }, label = { Text("All") }) }
                items(MuscleGroup.values().toList()) { g ->
                    FilterChip(selected = state.filter == g, onClick = { vm.setFilter(if (state.filter == g) null else g) }, label = { Text(g.label) })
                }
            }
            Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                val q = state.query.trim()
                if (q.isNotEmpty() && state.filtered.none { it.name.equals(q, ignoreCase = true) }) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable { createName = q }.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(Modifier.width(14.dp))
                            Text("Create \"$q\"", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (q.isEmpty() && state.filter == null && state.recent.isNotEmpty()) {
                    stickyHeader { PickerHeader("Recent") }
                    items(state.recent, key = { "r-" + it.id }) { ex ->
                        PickerRow(ex, selected = ex.id in state.selected, onClick = { vm.toggle(ex.id) })
                    }
                    stickyHeader { PickerHeader("All exercises") }
                }
                items(state.filtered, key = { it.id }) { ex ->
                    PickerRow(ex, selected = ex.id in state.selected, onClick = { vm.toggle(ex.id) })
                }
            }
            if (state.selected.isNotEmpty()) {
                Button(
                    onClick = { val ids = state.orderedSelection(); vm.reset(); onPicked(ids) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Add ${state.selected.size} exercise${if (state.selected.size > 1) "s" else ""}", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    createName?.let { initial ->
        CreateExerciseDialog(
            initialName = initial,
            onDismiss = { createName = null },
            onCreate = { name, equipment, muscle ->
                scope.launch {
                    val created = vm.create(name, equipment, muscle)
                    vm.toggle(created.id)
                    vm.setQuery("")
                }
                createName = null
            }
        )
    }
}

@Composable
private fun PickerHeader(text: String) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PickerRow(ex: ExerciseEntity, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(ex.name, style = MaterialTheme.typography.titleMedium)
            Text("${ex.equipment.label} · ${ex.muscleGroup.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateExerciseDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onCreate: (String, Equipment, MuscleGroup) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var equipment by remember { mutableStateOf(Equipment.BARBELL) }
    var muscle by remember { mutableStateOf(MuscleGroup.CHEST) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New exercise") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("Equipment", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Equipment.values().forEach { e ->
                        FilterChip(selected = equipment == e, onClick = { equipment = e }, label = { Text(e.label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Muscle group", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MuscleGroup.values().forEach { g ->
                        FilterChip(selected = muscle == g, onClick = { muscle = g }, label = { Text(g.label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim(), equipment, muscle) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
