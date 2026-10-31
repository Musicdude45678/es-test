package com.reps.app.data.model

enum class Equipment(val label: String, val usesBar: Boolean, val perHand: Boolean = false) {
    BARBELL("Barbell", usesBar = true),
    EZ_BAR("EZ bar", usesBar = true),
    TRAP_BAR("Trap bar", usesBar = true),
    SMITH("Smith machine", usesBar = true),
    DUMBBELL("Dumbbell", usesBar = false, perHand = true),
    KETTLEBELL("Kettlebell", usesBar = false),
    MACHINE("Machine", usesBar = false),
    CABLE("Cable", usesBar = false),
    BODYWEIGHT("Bodyweight", usesBar = false),
    OTHER("Other", usesBar = false);

    /** Typical bar weight for this equipment when no override is set (kg). */
    fun defaultBarKg(defaultOlympicBarKg: Double): Double = when (this) {
        BARBELL -> defaultOlympicBarKg
        EZ_BAR -> 10.0
        TRAP_BAR -> 25.0
        SMITH -> 0.0
        else -> 0.0
    }
}

enum class MuscleGroup(val label: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    LEGS("Legs"),
    GLUTES("Glutes"),
    CORE("Core"),
    FULL_BODY("Full body"),
    OTHER("Other")
}

enum class WeightUnit(val label: String, val perKg: Double) {
    KG("kg", 1.0),
    LB("lb", 2.2046226218);

    fun fromKg(kg: Double): Double = kg * perKg
    fun toKg(value: Double): Double = value / perKg
}

enum class ThemeMode { SYSTEM, DARK, LIGHT }
