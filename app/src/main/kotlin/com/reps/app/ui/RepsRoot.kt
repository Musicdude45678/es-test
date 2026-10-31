package com.reps.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reps.app.AppContainer
import com.reps.app.ui.history.HistoryScreen
import com.reps.app.ui.history.WorkoutDetailScreen
import com.reps.app.ui.home.HomeScreen
import com.reps.app.ui.navigation.Routes
import com.reps.app.ui.progress.ExerciseDetailScreen
import com.reps.app.ui.progress.ExerciseEditScreen
import com.reps.app.ui.progress.ProgressScreen
import com.reps.app.ui.routines.RoutineEditorScreen
import com.reps.app.ui.settings.SettingsScreen
import com.reps.app.ui.settings.SyncScreen
import com.reps.app.ui.sync.ConnectDialogHost
import com.reps.app.ui.workout.ActiveWorkoutScreen

val LocalSnackbar = compositionLocalOf<SnackbarHostState> { error("Snackbar host not provided") }

fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun RepsRoot(container: AppContainer) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalContainer provides container, LocalSnackbar provides snackbar) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
        ) { _ ->
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { if (initialState.destination.route in Routes.topLevel && targetState.destination.route in Routes.topLevel) fadeIn(tween(150)) else slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(200)) },
                exitTransition = { if (initialState.destination.route in Routes.topLevel && targetState.destination.route in Routes.topLevel) fadeOut(tween(150)) else fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { slideOutHorizontally(tween(240)) { it / 6 } + fadeOut(tween(200)) }
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        nav = nav,
                        onOpenWorkout = { nav.navigate(Routes.workout(it)) },
                        onOpenWorkoutDetail = { nav.navigate(Routes.workoutDetail(it)) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        onEditRoutine = { nav.navigate(Routes.routineEdit(it)) }
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(nav = nav, onOpenWorkoutDetail = { nav.navigate(Routes.workoutDetail(it)) })
                }
                composable(Routes.PROGRESS) {
                    ProgressScreen(
                        nav = nav,
                        onOpenExercise = { nav.navigate(Routes.exercise(it)) },
                        onCreateExercise = { nav.navigate(Routes.exerciseEdit(null)) }
                    )
                }
                composable(Routes.WORKOUT, arguments = listOf(navArgument("workoutId") { type = NavType.StringType })) { entry ->
                    val id = entry.arguments?.getString("workoutId") ?: return@composable
                    ActiveWorkoutScreen(
                        workoutId = id,
                        onBack = { nav.popBackStack() },
                        onFinished = { finishedId ->
                            nav.navigate(Routes.workoutDetail(finishedId)) { popUpTo(Routes.HOME) }
                        },
                        onDiscarded = { nav.popBackStack(Routes.HOME, inclusive = false) },
                        onOpenExercise = { nav.navigate(Routes.exercise(it)) }
                    )
                }
                composable(Routes.WORKOUT_DETAIL, arguments = listOf(navArgument("workoutId") { type = NavType.StringType })) { entry ->
                    val id = entry.arguments?.getString("workoutId") ?: return@composable
                    WorkoutDetailScreen(
                        workoutId = id,
                        onBack = { nav.popBackStack() },
                        onOpenWorkout = { nav.navigate(Routes.workout(it)) },
                        onOpenExercise = { nav.navigate(Routes.exercise(it)) },
                        onEditRoutine = { nav.navigate(Routes.routineEdit(it)) }
                    )
                }
                composable(Routes.EXERCISE, arguments = listOf(navArgument("exerciseId") { type = NavType.StringType })) { entry ->
                    val id = entry.arguments?.getString("exerciseId") ?: return@composable
                    ExerciseDetailScreen(
                        exerciseId = id,
                        onBack = { nav.popBackStack() },
                        onEdit = { nav.navigate(Routes.exerciseEdit(id)) },
                        onOpenWorkoutDetail = { nav.navigate(Routes.workoutDetail(it)) }
                    )
                }
                composable(
                    Routes.EXERCISE_EDIT,
                    arguments = listOf(navArgument("exerciseId") { type = NavType.StringType; nullable = true; defaultValue = null })
                ) { entry ->
                    ExerciseEditScreen(exerciseId = entry.arguments?.getString("exerciseId"), onBack = { nav.popBackStack() })
                }
                composable(
                    Routes.ROUTINE_EDIT,
                    arguments = listOf(navArgument("routineId") { type = NavType.StringType; nullable = true; defaultValue = null })
                ) { entry ->
                    RoutineEditorScreen(routineId = entry.arguments?.getString("routineId"), onBack = { nav.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { nav.popBackStack() }, onOpenSync = { nav.navigate(Routes.SYNC) })
                }
                composable(Routes.SYNC) {
                    SyncScreen(onBack = { nav.popBackStack() })
                }
            }
        }
        ConnectDialogHost()
    }
}
