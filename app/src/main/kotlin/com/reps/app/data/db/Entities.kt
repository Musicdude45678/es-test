package com.reps.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.reps.app.data.model.Equipment
import com.reps.app.data.model.MuscleGroup
import kotlinx.serialization.Serializable

@Entity(tableName = "exercises")
@Serializable
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val equipment: Equipment,
    val muscleGroup: MuscleGroup,
    /** Per-exercise bar weight override in kg. Null means "use the default for this equipment". */
    val barWeightKg: Double? = null,
    val isCustom: Boolean = false,
    val isArchived: Boolean = false,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "workouts",
    foreignKeys = [ForeignKey(RoutineEntity::class, ["id"], ["routineId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("startedAt"), Index("endedAt"), Index("routineId")]
)
@Serializable
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val notes: String = "",
    val routineId: String? = null
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(WorkoutEntity::class, ["id"], ["workoutId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("workoutId"), Index("exerciseId")]
)
@Serializable
data class WorkoutExerciseEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val exerciseId: String,
    val position: Int,
    val notes: String = ""
)

@Entity(
    tableName = "sets",
    foreignKeys = [ForeignKey(WorkoutExerciseEntity::class, ["id"], ["workoutExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutExerciseId"), Index("completedAt")]
)
@Serializable
data class SetEntity(
    @PrimaryKey val id: String,
    val workoutExerciseId: String,
    val position: Int,
    /** Total weight lifted in kg, including the bar when a bar is used. */
    val weightKg: Double,
    val reps: Int,
    /** Bar weight in kg recorded at the time of logging (null for non-bar equipment). */
    val barWeightKg: Double? = null,
    val isWarmup: Boolean = false,
    val completedAt: Long
)

@Entity(tableName = "routines")
@Serializable
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long? = null
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(RoutineEntity::class, ["id"], ["routineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("routineId"), Index("exerciseId")]
)
@Serializable
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val exerciseId: String,
    val position: Int,
    val targetSets: Int = 3,
    val targetReps: Int = 8
)

// ---------- Relations ----------

data class WorkoutExerciseWithSets(
    @Embedded val workoutExercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<SetEntity>
) {
    val sortedSets: List<SetEntity> get() = sets.sortedBy { it.position }
}

data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(entity = WorkoutExerciseEntity::class, parentColumn = "id", entityColumn = "workoutId")
    val exercises: List<WorkoutExerciseWithSets>
) {
    val sortedExercises: List<WorkoutExerciseWithSets> get() = exercises.sortedBy { it.workoutExercise.position }
    val totalSets: Int get() = exercises.sumOf { ex -> ex.sets.count { !it.isWarmup } }
    val totalVolumeKg: Double get() = exercises.sumOf { ex -> ex.sets.filter { !it.isWarmup }.sumOf { it.weightKg * it.reps } }
}

data class RoutineExerciseWithExercise(
    @Embedded val routineExercise: RoutineExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity
)

data class RoutineWithExercises(
    @Embedded val routine: RoutineEntity,
    @Relation(entity = RoutineExerciseEntity::class, parentColumn = "id", entityColumn = "routineId")
    val exercises: List<RoutineExerciseWithExercise>
) {
    val sortedExercises: List<RoutineExerciseWithExercise> get() = exercises.sortedBy { it.routineExercise.position }
}

/** A logged set with the context needed for history and progress. */
data class SetRecord(
    @Embedded val set: SetEntity,
    val exerciseId: String,
    val workoutId: String,
    val workoutStartedAt: Long
)

data class WorkoutSummary(
    val id: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val exerciseCount: Int,
    val setCount: Int,
    val volumeKg: Double
)
