package com.reps.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.reps.app.data.model.Equipment
import com.reps.app.data.model.MuscleGroup

class Converters {
    @TypeConverter fun equipmentToString(value: Equipment): String = value.name
    @TypeConverter fun stringToEquipment(value: String): Equipment = runCatching { Equipment.valueOf(value) }.getOrDefault(Equipment.OTHER)
    @TypeConverter fun muscleToString(value: MuscleGroup): String = value.name
    @TypeConverter fun stringToMuscle(value: String): MuscleGroup = runCatching { MuscleGroup.valueOf(value) }.getOrDefault(MuscleGroup.OTHER)
}

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        SetEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun routineDao(): RoutineDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "reps.db")
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
