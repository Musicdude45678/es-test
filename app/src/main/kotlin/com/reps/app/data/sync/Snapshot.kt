package com.reps.app.data.sync

import androidx.room.withTransaction
import com.reps.app.data.db.AppDatabase
import com.reps.app.data.db.ExerciseEntity
import com.reps.app.data.db.RoutineEntity
import com.reps.app.data.db.RoutineExerciseEntity
import com.reps.app.data.db.SetEntity
import com.reps.app.data.db.WorkoutEntity
import com.reps.app.data.db.WorkoutExerciseEntity
import com.reps.app.data.model.WeightUnit
import com.reps.app.data.prefs.Settings
import com.reps.app.data.prefs.SettingsRepository
import com.reps.app.data.repo.ChangeNotifier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SnapshotSettings(
    val unit: String = "KG",
    val defaultBarWeightKg: Double = 20.0,
    val weightIncrement: Double = 2.5,
    val restTimerSeconds: Int = 90
)

/** Full export of the user's data. This is what gets backed up to the server and restored on reinstall. */
@Serializable
data class Snapshot(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val app: String = "reps-android",
    val appVersion: String = "",
    val settings: SnapshotSettings = SnapshotSettings(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val workouts: List<WorkoutEntity> = emptyList(),
    val workoutExercises: List<WorkoutExerciseEntity> = emptyList(),
    val sets: List<SetEntity> = emptyList(),
    val routines: List<RoutineEntity> = emptyList(),
    val routineExercises: List<RoutineExerciseEntity> = emptyList()
) {
    val completedWorkoutCount: Int get() = workouts.count { it.endedAt != null }
}

data class RestoreSummary(val workouts: Int, val sets: Int, val exercises: Int, val exportedAt: Long)

class SnapshotRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val changes: ChangeNotifier,
    private val appVersion: String
) {
    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    suspend fun export(): Snapshot {
        val s = settings.current()
        return Snapshot(
            exportedAt = System.currentTimeMillis(),
            appVersion = appVersion,
            settings = SnapshotSettings(s.unit.name, s.defaultBarWeightKg, s.weightIncrement, s.restTimerSeconds),
            exercises = db.exerciseDao().getAll(),
            workouts = db.workoutDao().getAllWorkouts(),
            workoutExercises = db.workoutDao().getAllWorkoutExercises(),
            sets = db.workoutDao().getAllSets(),
            routines = db.routineDao().getAllRoutines(),
            routineExercises = db.routineDao().getAllRoutineExercises()
        )
    }

    suspend fun exportJson(): String = json.encodeToString(Snapshot.serializer(), export())

    fun parse(text: String): Snapshot = json.decodeFromString(Snapshot.serializer(), text)

    /** Replaces all local data with the snapshot. */
    suspend fun restore(snapshot: Snapshot, applySettings: Boolean = true): RestoreSummary {
        db.withTransaction {
            db.workoutDao().deleteAllWorkouts()
            db.routineDao().deleteAll()
            db.exerciseDao().deleteAll()
            db.exerciseDao().upsertAll(snapshot.exercises)
            db.routineDao().insertRoutines(snapshot.routines)
            val exerciseIds = snapshot.exercises.map { it.id }.toSet()
            val routineIds = snapshot.routines.map { it.id }.toSet()
            db.routineDao().insertRoutineExercises(snapshot.routineExercises.filter { it.exerciseId in exerciseIds && it.routineId in routineIds })
            db.workoutDao().insertWorkouts(snapshot.workouts.map { if (it.routineId != null && it.routineId !in routineIds) it.copy(routineId = null) else it })
            val workoutIds = snapshot.workouts.map { it.id }.toSet()
            val wes = snapshot.workoutExercises.filter { it.workoutId in workoutIds && it.exerciseId in exerciseIds }
            db.workoutDao().insertWorkoutExercises(wes)
            val weIds = wes.map { it.id }.toSet()
            db.workoutDao().insertSets(snapshot.sets.filter { it.workoutExerciseId in weIds })
        }
        if (applySettings) {
            runCatching { WeightUnit.valueOf(snapshot.settings.unit) }.getOrNull()?.let { settings.setUnit(it) }
            settings.setDefaultBarWeightKg(snapshot.settings.defaultBarWeightKg)
            settings.setWeightIncrement(snapshot.settings.weightIncrement)
            settings.setRestTimerSeconds(snapshot.settings.restTimerSeconds)
        }
        changes.notifyChanged()
        return RestoreSummary(snapshot.completedWorkoutCount, snapshot.sets.size, snapshot.exercises.size, snapshot.exportedAt)
    }

    /** Flat CSV of every logged set, for spreadsheets. */
    suspend fun exportCsv(current: Settings): String {
        val snap = export()
        val exById = snap.exercises.associateBy { it.id }
        val weById = snap.workoutExercises.associateBy { it.id }
        val wById = snap.workouts.associateBy { it.id }
        val unit = current.unit
        val sb = StringBuilder("date,time,workout,exercise,equipment,set,weight_${unit.label},bar_${unit.label},reps,warmup\n")
        snap.sets.sortedBy { it.completedAt }.forEach { s ->
            val we = weById[s.workoutExerciseId] ?: return@forEach
            val ex = exById[we.exerciseId] ?: return@forEach
            val w = wById[we.workoutId] ?: return@forEach
            val date = java.time.Instant.ofEpochMilli(s.completedAt).atZone(java.time.ZoneId.systemDefault())
            sb.append(date.toLocalDate()).append(',')
                .append(date.toLocalTime().withNano(0)).append(',')
                .append(csv(w.name)).append(',')
                .append(csv(ex.name)).append(',')
                .append(ex.equipment.label).append(',')
                .append(s.position + 1).append(',')
                .append(fmt(unit.fromKg(s.weightKg))).append(',')
                .append(s.barWeightKg?.let { fmt(unit.fromKg(it)) } ?: "").append(',')
                .append(s.reps).append(',')
                .append(if (s.isWarmup) "yes" else "no").append('\n')
        }
        return sb.toString()
    }

    private fun fmt(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    private fun csv(s: String): String = if (s.contains(',') || s.contains('"') || s.contains('\n')) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
