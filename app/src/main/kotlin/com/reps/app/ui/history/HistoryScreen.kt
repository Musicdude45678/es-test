package com.reps.app.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.reps.app.AppContainer
import com.reps.app.data.db.WorkoutSummary
import com.reps.app.data.prefs.Settings
import com.reps.app.domain.Dates
import com.reps.app.ui.appViewModel
import com.reps.app.ui.collectAsStateWithLifecycleCompat
import com.reps.app.ui.components.EmptyState
import com.reps.app.ui.home.WorkoutRow
import com.reps.app.ui.navigation.RepsBottomBar
import com.reps.app.ui.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryState(val loaded: Boolean = false, val groups: List<Pair<String, List<WorkoutSummary>>> = emptyList(), val settings: Settings = Settings())

class HistoryViewModel(container: AppContainer) : ViewModel() {
    val state = combine(container.repository.observeHistory(), container.settings.settings) { history, settings ->
        HistoryState(
            loaded = true,
            groups = history.groupBy { Dates.monthHeader(it.startedAt) }.map { it.key to it.value },
            settings = settings
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(nav: NavHostController, onOpenWorkoutDetail: (String) -> Unit) {
    val vm = appViewModel { HistoryViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycleCompat()
    Scaffold(
        topBar = { TopAppBar(title = { Text("History") }) },
        bottomBar = { RepsBottomBar(nav, Routes.HISTORY) }
    ) { padding ->
        if (state.loaded && state.groups.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState(Icons.Outlined.History, "No workouts yet", "Finish your first workout and it will show up here.")
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.groups.forEach { (month, workouts) ->
                stickyHeader {
                    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(month, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(workouts, key = { it.id }) { w ->
                    WorkoutRow(workout = w, unit = state.settings.unit, onClick = { onOpenWorkoutDetail(w.id) })
                }
            }
        }
    }
}
