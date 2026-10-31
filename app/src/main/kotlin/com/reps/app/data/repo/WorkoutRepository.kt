package com.reps.app.data.repo

import androidx.room.withTransaction
import com.reps.app.data.db.AppDatabase
import com.reps.app.data.db.ExerciseEntity
import com.reps.app.data.db.RoutineEntity
import com.reps.app.data.db.RoutineExerciseEntity
import com.reps.app.data.db.RoutineWithExercises
import com.reps.app.data.db.SetEntity
import com.reps.app.data.db.SetRecord
import com.reps.app.data.db.WorkoutEntity
import com.reps.app.data.db.WorkoutExerciseEntity
import com.reps.app.data.db.WorkoutSummary
import com.reps.app.data.db.WorkoutWithExercises
import com.reps.app.data.model.Equipment
import com.reps.app.data.model.MuscleGroup
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class WorkoutRepository(private val db: AppDatabase, private val changes: ChangeNotifier) {
    private val exercises = db.exerciseDao()
    private val workouts = db.workoutDao()
    private val routines = db.routineDao()

    private fun newId() = UUID.randomUUID().toString()
    private fun now() = System.currentTimeMillis()

    // ---------- Exercises ----------
    fun observeExercises(): Flow<List<ExerciseEntity>> = exercises.observeActive()
    fun observeAllExercises(): Flow<List<ExerciseEntity>> = exercises.observeAll()
    fun observeExercise(id: String): Flow<ExerciseEntity?> = exercises.observeById(id)
    fun observeRecentExerciseIds(): Flow<List<String>> = exercises.observeRecentExerciseIds()
    suspend fun getExercise(id: String): ExerciseEntity? = exercises.getById(id)

    suspend fun createExercise(name: String, equipment: Equipment, muscle: MuscleGroup, barWeightKg: Double? = null): ExerciseEntity {
        val e = ExerciseEntity(
            id = newId(), name = name.trim(), equipment = equipment, muscleGroup = muscle,
            barWeightKg = barWeightKg, isCustom = true, createdAt = now(), updatedAt = now()
        )
        exercises.upsert(e)
        changes.notifyChanged()
        return e
    }

    suspend fun updateExercise(exercise: ExerciseEntity) {
        exercises.upsert(exercise.copy(updatedAt = now()))
        changes.notifyChanged()
    }

    suspend fun setExerciseArchived(id: String, archived: Boolean) {
        exercises.getById(id)?.let { exercises.upsert(it.copy(isArchived = archived, updatedAt = now())) }
        changes.notifyChanged()
    }

    /** Deletes an exercise if it has never been used, otherwise archives it. Returns true if deleted. */
    suspend fun deleteOrArchiveExercise(id: String): Boolean {
        val used = exercises.usageCount(id) > 0
        if (used) setExerciseArchived(id, true) else { exercises.delete(id); changes.notifyChanged() }
        return !used
    }

    // ---------- Workouts ----------
    fun observeActiveWorkout(): Flow<WorkoutWithExercises?> = workouts.observeActive()
    fun observeWorkout(id: String): Flow<WorkoutWithExercises?> = workouts.observeWithExercises(id)
    fun observeHistory(): Flow<List<WorkoutSummary>> = workouts.observeHistory()
    fun observeCompletedCount(): Flow<Int> = workouts.observeCompletedCount()
    fun observeAllRecords(): Flow<List<SetRecord>> = workouts.observeAllRecords()
    fun observeExerciseRecords(exerciseId: String): Flow<List<SetRecord>> = workouts.observeExerciseRecords(exerciseId)
    suspend fun getWorkout(id: String): WorkoutWithExercises? = workouts.getWithExercises(id)
    suspend fun getActiveWorkout(): WorkoutEntity? = workouts.getActive()

    suspend fun startEmptyWorkout(name: String = defaultWorkoutName()): String {
        workouts.getActive()?.let { return it.id }
        val w = WorkoutEntity(id = newId(), name = name, startedAt = now())
        workouts.insertWorkout(w)
        changes.notifyChanged()
        return w.id
    }

    suspend fun startFromRoutine(routine: RoutineWithExercises): String {
        workouts.getActive()?.let { return it.id }
        val w = WorkoutEntity(id = newId(), name = routine.routine.name, startedAt = now(), routineId = routine.routine.id)
        db.withTransaction {
            workouts.insertWorkout(w)
            routine.sortedExercises.forEachIndexed { i, re ->
                workouts.insertWorkoutExercise(WorkoutExerciseEntity(newId(), w.id, re.exercise.id, i))
            }
            routines.markUsed(routine.routine.id, now())
        }
        changes.notifyChanged()
        return w.id
    }

    suspend fun startFromWorkout(sourceId: String): String? {
        workouts.getActive()?.let { return it.id }
        val source = workouts.getWithExercises(sourceId) ?: return null
        val w = WorkoutEntity(id = newId(), name = source.workout.name, startedAt = now(), routineId = source.workout.routineId)
        db.withTransaction {
            workouts.insertWorkout(w)
            source.sortedExercises.forEachIndexed { i, ex ->
                workouts.insertWorkoutExercise(WorkoutExerciseEntity(newId(), w.id, ex.exercise.id, i))
            }
        }
        changes.notifyChanged()
        return w.id
    }

    suspend fun renameWorkout(id: String, name: String) {
        workouts.getById(id)?.let { workouts.updateWorkout(it.copy(name = name.trim().ifBlank { defaultWorkoutName() })) }
        changes.notifyChanged()
    }

    suspend fun setWorkoutNotes(id: String, notes: String) {
        workouts.getById(id)?.let { workouts.updateWorkout(it.copy(notes = notes)) }
        changes.notifyChanged()
    }

    suspend fun addExercisesToWorkout(workoutId: String, exerciseIds: List<String>) {
        db.withTransaction {
            var pos = workouts.nextExercisePosition(workoutId)
            exerciseIds.forEach { exId ->
                workouts.insertWorkoutExercise(WorkoutExerciseEntity(newId(), workoutId, exId, pos++))
            }
        }
        changes.notifyChanged()
    }

    suspend fun removeWorkoutExercise(workoutExerciseId: String) {
        workouts.deleteWorkoutExercise(workoutExerciseId)
        changes.notifyChanged()
    }

    suspend fun moveWorkoutExercise(workout: WorkoutWithExercises, from: Int, to: Int) {
        val list = workout.sortedExercises.map { it.workoutExercise }.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        db.withTransaction { list.forEachIndexed { i, we -> if (we.position != i) workouts.updateWorkoutExercise(we.copy(position = i)) } }
        changes.notifyChanged()
    }

    suspend fun logSet(workoutExerciseId: String, weightKg: Double, reps: Int, barWeightKg: Double?, isWarmup: Boolean): SetEntity {
        val set = SetEntity(
            id = newId(), workoutExerciseId = workoutExerciseId, position = workouts.nextSetPosition(workoutExerciseId),
            weightKg = weightKg, reps = reps, barWeightKg = barWeightKg, isWarmup = isWarmup, completedAt = now()
        )
        workouts.insertSet(set)
        changes.notifyChanged()
        return set
    }

    suspend fun updateSet(set: SetEntity) { workouts.updateSet(set); changes.notifyChanged() }

    suspend fun deleteSet(set: SetEntity) {
        db.withTransaction {
            workouts.deleteSet(set)
            workouts.setsFor(set.workoutExerciseId).forEachIndexed { i, s -> if (s.position != i) workouts.updateSet(s.copy(position = i)) }
        }
        changes.notifyChanged()
    }

    suspend fun finishWorkout(id: String) {
        val w = workouts.getById(id) ?: return
        workouts.updateWorkout(w.copy(endedAt = now()))
        changes.notifyChanged()
    }

    suspend fun deleteWorkout(id: String) { workouts.deleteWorkout(id); changes.notifyChanged() }

    suspend fun previousSets(exerciseId: String, excludeWorkoutId: String): List<SetEntity> =
        workouts.previousSetsFor(exerciseId, excludeWorkoutId)

    /** True when this set is heavier than anything logged for the exercise before it. */
    suspend fun isPersonalRecord(exerciseId: String, set: SetEntity): Boolean {
        if (set.isWarmup || set.reps <= 0 || set.weightKg <= 0) return false
        val best = workouts.maxWeightBefore(exerciseId, set.completedAt, set.id) ?: return true
        return set.weightKg > best + 0.0001
    }

    suspend fun lastCompletedWorkout(): WorkoutWithExercises? = workouts.getLastCompleted()

    // ---------- Routines ----------
    fun observeRoutines(): Flow<List<RoutineWithExercises>> = routines.observeAll()
    fun observeRoutine(id: String): Flow<RoutineWithExercises?> = routines.observeById(id)
    suspend fun getRoutine(id: String): RoutineWithExercises? = routines.getById(id)

    suspend fun saveRoutine(id: String?, name: String, exerciseIds: List<String>): String {
        val routineId = id ?: newId()
        val existing = id?.let { routines.getById(it)?.routine }
        val routine = existing?.copy(name = name.trim(), updatedAt = now())
            ?: RoutineEntity(id = routineId, name = name.trim(), createdAt = now(), updatedAt = now())
        db.withTransaction {
            routines.upsertRoutine(routine)
            routines.deleteExercisesFor(routineId)
            routines.insertRoutineExercises(exerciseIds.mapIndexed { i, exId -> RoutineExerciseEntity(newId(), routineId, exId, i) })
        }
        changes.notifyChanged()
        return routineId
    }

    suspend fun saveWorkoutAsRoutine(workoutId: String, name: String): String? {
        val w = workouts.getWithExercises(workoutId) ?: return null
        return saveRoutine(null, name, w.sortedExercises.map { it.exercise.id }.distinct())
    }

    suspend fun deleteRoutine(id: String) { routines.deleteRoutine(id); changes.notifyChanged() }

    companion object {
        fun defaultWorkoutName(): String {
            val hour = java.time.LocalTime.now().hour
            return when {
                hour < 12 -> "Morning workout"
                hour < 17 -> "Afternoon workout"
                else -> "Evening workout"
            }
        }
    }
}
