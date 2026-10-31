package com.reps.app.data.db

import com.reps.app.data.model.Equipment
import com.reps.app.data.model.Equipment.BARBELL
import com.reps.app.data.model.Equipment.BODYWEIGHT
import com.reps.app.data.model.Equipment.CABLE
import com.reps.app.data.model.Equipment.DUMBBELL
import com.reps.app.data.model.Equipment.EZ_BAR
import com.reps.app.data.model.Equipment.KETTLEBELL
import com.reps.app.data.model.Equipment.MACHINE
import com.reps.app.data.model.Equipment.SMITH
import com.reps.app.data.model.Equipment.TRAP_BAR
import com.reps.app.data.model.MuscleGroup
import com.reps.app.data.model.MuscleGroup.BACK
import com.reps.app.data.model.MuscleGroup.BICEPS
import com.reps.app.data.model.MuscleGroup.CHEST
import com.reps.app.data.model.MuscleGroup.CORE
import com.reps.app.data.model.MuscleGroup.FULL_BODY
import com.reps.app.data.model.MuscleGroup.GLUTES
import com.reps.app.data.model.MuscleGroup.LEGS
import com.reps.app.data.model.MuscleGroup.SHOULDERS
import com.reps.app.data.model.MuscleGroup.TRICEPS
import java.util.UUID

object SeedData {
    private data class Seed(val name: String, val equipment: Equipment, val muscle: MuscleGroup)

    private val seeds = listOf(
        // Barbell
        Seed("Back Squat", BARBELL, LEGS),
        Seed("Front Squat", BARBELL, LEGS),
        Seed("Bench Press", BARBELL, CHEST),
        Seed("Incline Bench Press", BARBELL, CHEST),
        Seed("Close-Grip Bench Press", BARBELL, TRICEPS),
        Seed("Overhead Press", BARBELL, SHOULDERS),
        Seed("Push Press", BARBELL, SHOULDERS),
        Seed("Deadlift", BARBELL, BACK),
        Seed("Romanian Deadlift", BARBELL, LEGS),
        Seed("Sumo Deadlift", BARBELL, LEGS),
        Seed("Barbell Row", BARBELL, BACK),
        Seed("Pendlay Row", BARBELL, BACK),
        Seed("Hip Thrust", BARBELL, GLUTES),
        Seed("Barbell Curl", BARBELL, BICEPS),
        Seed("Good Morning", BARBELL, LEGS),
        Seed("Barbell Lunge", BARBELL, LEGS),
        Seed("Barbell Shrug", BARBELL, SHOULDERS),
        Seed("EZ Bar Curl", EZ_BAR, BICEPS),
        Seed("Skull Crusher", EZ_BAR, TRICEPS),
        Seed("Trap Bar Deadlift", TRAP_BAR, LEGS),
        Seed("Smith Machine Squat", SMITH, LEGS),
        Seed("Smith Machine Bench Press", SMITH, CHEST),
        // Dumbbell
        Seed("Dumbbell Bench Press", DUMBBELL, CHEST),
        Seed("Incline Dumbbell Press", DUMBBELL, CHEST),
        Seed("Dumbbell Fly", DUMBBELL, CHEST),
        Seed("Dumbbell Shoulder Press", DUMBBELL, SHOULDERS),
        Seed("Arnold Press", DUMBBELL, SHOULDERS),
        Seed("Lateral Raise", DUMBBELL, SHOULDERS),
        Seed("Rear Delt Fly", DUMBBELL, SHOULDERS),
        Seed("Dumbbell Row", DUMBBELL, BACK),
        Seed("Dumbbell Curl", DUMBBELL, BICEPS),
        Seed("Hammer Curl", DUMBBELL, BICEPS),
        Seed("Incline Dumbbell Curl", DUMBBELL, BICEPS),
        Seed("Overhead Tricep Extension", DUMBBELL, TRICEPS),
        Seed("Goblet Squat", DUMBBELL, LEGS),
        Seed("Dumbbell Lunge", DUMBBELL, LEGS),
        Seed("Bulgarian Split Squat", DUMBBELL, LEGS),
        Seed("Dumbbell Romanian Deadlift", DUMBBELL, LEGS),
        Seed("Dumbbell Shrug", DUMBBELL, SHOULDERS),
        // Machine
        Seed("Leg Press", MACHINE, LEGS),
        Seed("Hack Squat", MACHINE, LEGS),
        Seed("Leg Extension", MACHINE, LEGS),
        Seed("Leg Curl", MACHINE, LEGS),
        Seed("Calf Raise", MACHINE, LEGS),
        Seed("Hip Abduction", MACHINE, GLUTES),
        Seed("Hip Adduction", MACHINE, LEGS),
        Seed("Chest Press Machine", MACHINE, CHEST),
        Seed("Pec Deck", MACHINE, CHEST),
        Seed("Shoulder Press Machine", MACHINE, SHOULDERS),
        Seed("Lat Pulldown", MACHINE, BACK),
        Seed("Seated Row Machine", MACHINE, BACK),
        Seed("Assisted Pull-up", MACHINE, BACK),
        Seed("Preacher Curl Machine", MACHINE, BICEPS),
        Seed("Tricep Dip Machine", MACHINE, TRICEPS),
        Seed("Ab Crunch Machine", MACHINE, CORE),
        // Cable
        Seed("Cable Row", CABLE, BACK),
        Seed("Cable Fly", CABLE, CHEST),
        Seed("Tricep Pushdown", CABLE, TRICEPS),
        Seed("Cable Curl", CABLE, BICEPS),
        Seed("Face Pull", CABLE, SHOULDERS),
        Seed("Cable Lateral Raise", CABLE, SHOULDERS),
        Seed("Cable Crunch", CABLE, CORE),
        Seed("Cable Pull-through", CABLE, GLUTES),
        Seed("Straight-Arm Pulldown", CABLE, BACK),
        // Kettlebell
        Seed("Kettlebell Swing", KETTLEBELL, FULL_BODY),
        Seed("Kettlebell Goblet Squat", KETTLEBELL, LEGS),
        // Bodyweight (weight = added weight, 0 for none)
        Seed("Pull-up", BODYWEIGHT, BACK),
        Seed("Chin-up", BODYWEIGHT, BICEPS),
        Seed("Dip", BODYWEIGHT, TRICEPS),
        Seed("Push-up", BODYWEIGHT, CHEST),
        Seed("Hanging Leg Raise", BODYWEIGHT, CORE),
        Seed("Plank", BODYWEIGHT, CORE)
    )

    fun exercises(now: Long = System.currentTimeMillis()): List<ExerciseEntity> = seeds.map {
        ExerciseEntity(
            id = UUID.nameUUIDFromBytes("seed:${it.name}".toByteArray()).toString(),
            name = it.name,
            equipment = it.equipment,
            muscleGroup = it.muscle,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        )
    }
}
