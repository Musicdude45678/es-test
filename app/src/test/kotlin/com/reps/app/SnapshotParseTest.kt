package com.reps.app

import com.reps.app.data.sync.Snapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Parses an external backup file with the app's own serializer when REPS_SNAPSHOT_FILE points at one. */
class SnapshotParseTest {
    @Test fun externalSnapshotParses() {
        val path = System.getenv("REPS_SNAPSHOT_FILE")
        assumeTrue("REPS_SNAPSHOT_FILE not set", !path.isNullOrBlank())
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val snap = json.decodeFromString(Snapshot.serializer(), File(path!!).readText())
        val exIds = snap.exercises.map { it.id }.toSet()
        val weIds = snap.workoutExercises.map { it.id }.toSet()
        val wIds = snap.workouts.map { it.id }.toSet()
        assertTrue(snap.workoutExercises.all { it.exerciseId in exIds && it.workoutId in wIds })
        assertTrue(snap.sets.all { it.workoutExerciseId in weIds })
        assertTrue(snap.routineExercises.all { it.exerciseId in exIds })
        // round-trip through the serializer the app uses for export
        val again = json.decodeFromString(Snapshot.serializer(), json.encodeToString(Snapshot.serializer(), snap))
        assertEquals(snap, again)
        println("PARSED OK: exercises=${snap.exercises.size} workouts=${snap.workouts.size} completed=${snap.completedWorkoutCount} sets=${snap.sets.size} routines=${snap.routines.size}")
    }
}
