package com.reps.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE isArchived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeById(id: String): Flow<ExerciseEntity?>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    /** Exercise ids ordered by most recent use. */
    @Query(
        """
        SELECT we.exerciseId FROM workout_exercises we
        JOIN workouts w ON w.id = we.workoutId
        GROUP BY we.exerciseId
        ORDER BY MAX(w.startedAt) DESC
        LIMIT :limit
        """
    )
    fun observeRecentExerciseIds(limit: Int = 12): Flow<List<String>>

    @Upsert
    suspend fun upsert(exercise: ExerciseEntity)

    @Upsert
    suspend fun upsertAll(exercises: List<ExerciseEntity>)

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM workout_exercises WHERE exerciseId = :id")
    suspend fun usageCount(id: String): Int

    @Query("DELETE FROM exercises")
    suspend fun deleteAll()
}

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workouts WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutWithExercises?>

    @Query("SELECT * FROM workouts WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): WorkoutEntity?

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeWithExercises(id: String): Flow<WorkoutWithExercises?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWithExercises(id: String): WorkoutWithExercises?

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: String): WorkoutEntity?

    @Query(
        """
        SELECT w.id, w.name, w.startedAt, w.endedAt,
            (SELECT COUNT(*) FROM workout_exercises we WHERE we.workoutId = w.id) AS exerciseCount,
            (SELECT COUNT(*) FROM sets s JOIN workout_exercises we ON we.id = s.workoutExerciseId WHERE we.workoutId = w.id AND s.isWarmup = 0) AS setCount,
            (SELECT COALESCE(SUM(s.weightKg * s.reps), 0) FROM sets s JOIN workout_exercises we ON we.id = s.workoutExerciseId WHERE we.workoutId = w.id AND s.isWarmup = 0) AS volumeKg
        FROM workouts w
        WHERE w.endedAt IS NOT NULL
        ORDER BY w.startedAt DESC
        """
    )
    fun observeHistory(): Flow<List<WorkoutSummary>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE endedAt IS NOT NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLastCompleted(): WorkoutWithExercises?

    @Query("SELECT * FROM workouts")
    suspend fun getAllWorkouts(): List<WorkoutEntity>

    @Query("SELECT * FROM workout_exercises")
    suspend fun getAllWorkoutExercises(): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM sets")
    suspend fun getAllSets(): List<SetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity)

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercise(workoutExercise: WorkoutExerciseEntity)

    @Update
    suspend fun updateWorkoutExercise(workoutExercise: WorkoutExerciseEntity)

    @Query("DELETE FROM workout_exercises WHERE id = :id")
    suspend fun deleteWorkoutExercise(id: String)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun nextExercisePosition(workoutId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: SetEntity)

    @Update
    suspend fun updateSet(set: SetEntity)

    @Delete
    suspend fun deleteSet(set: SetEntity)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM sets WHERE workoutExerciseId = :workoutExerciseId")
    suspend fun nextSetPosition(workoutExerciseId: String): Int

    @Query("SELECT * FROM sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY position")
    suspend fun setsFor(workoutExerciseId: String): List<SetEntity>

    /** All sets for an exercise from completed workouts, oldest first. */
    @Query(
        """
        SELECT s.*, we.exerciseId AS exerciseId, w.id AS workoutId, w.startedAt AS workoutStartedAt
        FROM sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId
        JOIN workouts w ON w.id = we.workoutId
        WHERE we.exerciseId = :exerciseId AND w.endedAt IS NOT NULL
        ORDER BY w.startedAt ASC, s.position ASC
        """
    )
    fun observeExerciseRecords(exerciseId: String): Flow<List<SetRecord>>

    /** Every set from completed workouts, used for progress overviews and PR detection. */
    @Query(
        """
        SELECT s.*, we.exerciseId AS exerciseId, w.id AS workoutId, w.startedAt AS workoutStartedAt
        FROM sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId
        JOIN workouts w ON w.id = we.workoutId
        WHERE w.endedAt IS NOT NULL
        ORDER BY w.startedAt ASC, s.position ASC
        """
    )
    fun observeAllRecords(): Flow<List<SetRecord>>

    /** Sets performed for an exercise in the most recent completed workout other than [excludeWorkoutId]. */
    @Query(
        """
        SELECT s.* FROM sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId
        WHERE we.exerciseId = :exerciseId AND we.workoutId = (
            SELECT w.id FROM workouts w
            JOIN workout_exercises we2 ON we2.workoutId = w.id
            WHERE we2.exerciseId = :exerciseId AND w.endedAt IS NOT NULL AND w.id != :excludeWorkoutId
            ORDER BY w.startedAt DESC LIMIT 1
        )
        ORDER BY s.position
        """
    )
    suspend fun previousSetsFor(exerciseId: String, excludeWorkoutId: String): List<SetEntity>

    /** Best working-set weight for an exercise logged before [before] (any workout). */
    @Query(
        """
        SELECT MAX(s.weightKg) FROM sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId
        WHERE we.exerciseId = :exerciseId AND s.isWarmup = 0 AND s.completedAt < :before AND s.id != :excludeSetId
        """
    )
    suspend fun maxWeightBefore(exerciseId: String, before: Long, excludeSetId: String): Double?

    @Query("SELECT COUNT(*) FROM workouts WHERE endedAt IS NOT NULL")
    suspend fun completedCount(): Int

    @Query("SELECT COUNT(*) FROM workouts WHERE endedAt IS NOT NULL")
    fun observeCompletedCount(): Flow<Int>

    @Query("DELETE FROM workouts")
    suspend fun deleteAllWorkouts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkouts(workouts: List<WorkoutEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercises(items: List<WorkoutExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(items: List<SetEntity>)
}

@Dao
interface RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines ORDER BY COALESCE(lastUsedAt, createdAt) DESC")
    fun observeAll(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    fun observeById(id: String): Flow<RoutineWithExercises?>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getById(id: String): RoutineWithExercises?

    @Query("SELECT * FROM routines")
    suspend fun getAllRoutines(): List<RoutineEntity>

    @Query("SELECT * FROM routine_exercises")
    suspend fun getAllRoutineExercises(): List<RoutineExerciseEntity>

    @Upsert
    suspend fun upsertRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: String)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun deleteExercisesFor(routineId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineExercises(items: List<RoutineExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutines(items: List<RoutineEntity>)

    @Query("UPDATE routines SET lastUsedAt = :at WHERE id = :id")
    suspend fun markUsed(id: String, at: Long)

    @Query("DELETE FROM routines")
    suspend fun deleteAll()
}
