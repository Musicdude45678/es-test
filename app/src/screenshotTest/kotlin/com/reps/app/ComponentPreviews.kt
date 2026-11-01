package com.reps.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.reps.app.data.db.ExerciseEntity
import com.reps.app.data.db.SetEntity
import com.reps.app.data.db.WorkoutExerciseEntity
import com.reps.app.data.db.WorkoutExerciseWithSets
import com.reps.app.data.db.WorkoutSummary
import com.reps.app.data.model.Equipment
import com.reps.app.data.model.MuscleGroup
import com.reps.app.data.model.ThemeMode
import com.reps.app.data.model.WeightUnit
import com.reps.app.data.prefs.Settings
import com.reps.app.ui.components.StatTile
import com.reps.app.ui.components.Stepper
import com.reps.app.ui.home.HomeUiState
import com.reps.app.ui.home.WeekCard
import com.reps.app.ui.home.WorkoutRow
import com.reps.app.ui.theme.RepsTheme
import com.reps.app.ui.workout.ExerciseCard
import com.reps.app.ui.workout.ExerciseUi
import com.reps.app.ui.workout.PendingSet
import com.reps.app.ui.workout.RestTimerBar
import java.time.DayOfWeek

@Composable
private fun Frame(content: @Composable () -> Unit) {
    RepsTheme(mode = ThemeMode.DARK) { Surface { content() } }
}

private val bench = ExerciseEntity(id = "e1", name = "Smith Machine Incline Bench Press", equipment = Equipment.BARBELL, muscleGroup = MuscleGroup.CHEST, createdAt = 0, updatedAt = 0)
private val dbRow = ExerciseEntity(id = "e2", name = "Dumbbell Row", equipment = Equipment.DUMBBELL, muscleGroup = MuscleGroup.BACK, createdAt = 0, updatedAt = 0)
private fun sets(weId: String, vararg pairs: Pair<Double, Int>) = pairs.mapIndexed { i, (w, r) -> SetEntity(id = "$weId-s$i", workoutExerciseId = weId, position = i, weightKg = w, reps = r, barWeightKg = 20.0, isWarmup = i == 0, completedAt = i.toLong()) }

@PreviewTest
@Preview(name = "steppers 360", widthDp = 360, showBackground = true)
@Preview(name = "steppers 360 big font", widthDp = 360, fontScale = 1.3f, showBackground = true)
@Composable
fun StepperRowPreview() {
    Frame {
        Row(Modifier.padding(start = 18.dp, end = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stepper(label = "Per hand", value = "127.5", unit = "kg", onDecrement = {}, onIncrement = {}, onTapValue = {}, modifier = Modifier.weight(1.25f))
            Stepper(label = "Reps", value = "100", onDecrement = {}, onIncrement = {}, onTapValue = {}, modifier = Modifier.weight(1f))
        }
    }
}

@PreviewTest
@Preview(name = "stat tiles 360", widthDp = 360, showBackground = true)
@Preview(name = "stat tiles 360 big font", widthDp = 360, fontScale = 1.3f, showBackground = true)
@Composable
fun StatTilesPreview() {
    Frame {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Duration", "1h 12m", Modifier.weight(1f).fillMaxHeight())
                StatTile("Sets", "24", Modifier.weight(1f).fillMaxHeight())
                StatTile("Volume", "205.0k", Modifier.weight(1f).fillMaxHeight(), sub = "kg")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Best weight", "127.5", Modifier.weight(1f), sub = "kg × 12 reps")
                StatTile("Est. 1RM", "178.75", Modifier.weight(1f), sub = "kg · Epley")
            }
        }
    }
}

@PreviewTest
@Preview(name = "exercise card 360", widthDp = 360, showBackground = true)
@Preview(name = "exercise card 360 big font", widthDp = 360, fontScale = 1.3f, showBackground = true)
@Composable
fun ExerciseCardPreview() {
    val item = WorkoutExerciseWithSets(WorkoutExerciseEntity("we1", "w1", bench.id, 0), bench, sets("we1", 40.0 to 10, 100.0 to 8, 102.5 to 8))
    val ui = ExerciseUi(item = item, previous = sets("prev", 40.0 to 10, 100.0 to 8, 100.0 to 8, 100.0 to 6), pending = PendingSet(102.5, 8, 20.0), prSetIds = setOf("we1-s2"))
    Frame {
        Column(Modifier.padding(vertical = 8.dp)) {
            ExerciseCard(ui = ui, settings = Settings(), index = 0, count = 2, onLog = {}, onStepWeight = {}, onStepReps = {}, onTapWeight = {}, onTapReps = {}, onToggleWarmup = {}, onEditSet = {}, onRemove = {}, onMove = {}, onOpenHistory = {})
        }
    }
}

@PreviewTest
@Preview(name = "dumbbell card 360", widthDp = 360, showBackground = true)
@Composable
fun DumbbellCardPreview() {
    val item = WorkoutExerciseWithSets(WorkoutExerciseEntity("we2", "w1", dbRow.id, 0), dbRow, emptyList())
    val ui = ExerciseUi(item = item, previous = emptyList(), pending = PendingSet(24.0, 10, null, warmup = true), prSetIds = emptySet())
    Frame {
        Column(Modifier.padding(vertical = 8.dp)) {
            ExerciseCard(ui = ui, settings = Settings(unit = WeightUnit.LB), index = 1, count = 2, onLog = {}, onStepWeight = {}, onStepReps = {}, onTapWeight = {}, onTapReps = {}, onToggleWarmup = {}, onEditSet = {}, onRemove = {}, onMove = {}, onOpenHistory = {})
        }
    }
}

@PreviewTest
@Preview(name = "rest bar 360", widthDp = 360, showBackground = true)
@Composable
fun RestBarPreview() {
    Frame { RestTimerBar(remaining = 83, total = 90, onAdd = {}, onSkip = {}) }
}

@PreviewTest
@Preview(name = "home week card 360", widthDp = 360, showBackground = true)
@Preview(name = "home week card 360 big font", widthDp = 360, fontScale = 1.3f, showBackground = true)
@Composable
fun WeekCardPreview() {
    Frame {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WeekCard(HomeUiState(loaded = true, trainedDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), weekWorkouts = 3, weekSets = 48, weekVolumeKg = 12400.0, streakWeeks = 13))
            WorkoutRow(WorkoutSummary("w", "Lower body hypertrophy day B", 1_700_000_000_000, 1_700_004_000_000, 6, 22, 12345.0), WeightUnit.KG, onClick = {})
        }
    }
}
