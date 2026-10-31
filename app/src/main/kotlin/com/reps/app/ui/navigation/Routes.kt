package com.reps.app.ui.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val SYNC = "sync"
    const val WORKOUT = "workout/{workoutId}"
    const val WORKOUT_DETAIL = "workoutDetail/{workoutId}"
    const val EXERCISE = "exercise/{exerciseId}"
    const val EXERCISE_EDIT = "exerciseEdit?exerciseId={exerciseId}"
    const val ROUTINE_EDIT = "routineEdit?routineId={routineId}"

    val topLevel = setOf(HOME, HISTORY, PROGRESS)

    fun workout(id: String) = "workout/$id"
    fun workoutDetail(id: String) = "workoutDetail/$id"
    fun exercise(id: String) = "exercise/$id"
    fun exerciseEdit(id: String?) = if (id == null) "exerciseEdit" else "exerciseEdit?exerciseId=${Uri.encode(id)}"
    fun routineEdit(id: String?) = if (id == null) "routineEdit" else "routineEdit?routineId=${Uri.encode(id)}"
}
