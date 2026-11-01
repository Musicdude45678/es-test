package com.reps.app.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.reps.app.domain.Calc
import com.reps.app.domain.Dates
import com.reps.app.ui.appViewModel
import com.reps.app.ui.collectAsStateWithLifecycleCompat
import com.reps.app.ui.components.BarChart
import com.reps.app.ui.components.EmptyState
import com.reps.app.ui.components.SectionHeader
import com.reps.app.ui.components.Sparkline
import com.reps.app.ui.components.StatTile
import com.reps.app.ui.navigation.RepsBottomBar
import com.reps.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(nav: NavHostController, onOpenExercise: (String) -> Unit, onCreateExercise: () -> Unit) {
    val vm = appViewModel { ProgressViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycleCompat()
    val unit = state.settings.unit

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progress") },
                actions = { IconButton(onClick = onCreateExercise) { Icon(Icons.Default.Add, contentDescription = "New exercise") } }
            )
        },
        bottomBar = { RepsBottomBar(nav, Routes.PROGRESS) }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.loaded && !state.hasData) {
                item {
                    EmptyState(
                        Icons.Outlined.TrendingUp,
                        "Your progress will live here",
                        "Once you've finished a workout you'll see weekly volume, personal records and a trend line for every exercise."
                    )
                }
                return@LazyColumn
            }

            item {
                Row(Modifier.padding(horizontal = 20.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Workouts", state.totalWorkouts.toString(), Modifier.weight(1f).fillMaxHeight())
                    StatTile("Sets", state.totalSets.toString(), Modifier.weight(1f).fillMaxHeight())
                    StatTile("Lifted", Calc.formatVolume(state.totalVolumeKg, unit, withUnit = false), Modifier.weight(1f).fillMaxHeight(), sub = unit.label)
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Weekly volume", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            val thisWeek = state.weeks.lastOrNull()?.volumeKg ?: 0.0
                            Text(Calc.formatVolume(thisWeek, unit), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
                        }
                        Spacer(Modifier.height(14.dp))
                        BarChart(
                            values = state.weeks.map { it.volumeKg.toFloat() },
                            labels = state.weeks.map { Dates.weekLabel(it.startMillis) },
                            modifier = Modifier.fillMaxWidth().height(140.dp)
                        )
                    }
                }
            }

            if (state.recentPrs.isNotEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Recent records", style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(8.dp))
                            state.recentPrs.forEach { pr ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { onOpenExercise(pr.exercise.id) }.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(pr.exercise.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(Dates.relativeDay(pr.at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("${Calc.formatWeight(pr.weightKg, unit)} × ${pr.reps}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader("Exercises", modifier = Modifier.padding(top = 6.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Clear, contentDescription = "Clear") } },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            items(state.exercises, key = { it.exercise.id }) { s ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { onOpenExercise(s.exercise.id) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Best ${Calc.formatWeight(s.bestWeightKg, unit)} · ${s.sessions} session${if (s.sessions == 1) "" else "s"} · ${Dates.relativeDay(s.lastPerformed)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Sparkline(values = s.trend, modifier = Modifier.width(72.dp).height(32.dp))
                    }
                }
            }
        }
    }
}
