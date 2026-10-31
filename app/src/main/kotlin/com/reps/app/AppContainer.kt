package com.reps.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.reps.app.data.db.AppDatabase
import com.reps.app.data.db.SeedData
import com.reps.app.data.prefs.SettingsRepository
import com.reps.app.data.repo.ChangeNotifier
import com.reps.app.data.repo.WorkoutRepository
import com.reps.app.data.sync.SnapshotRepository
import com.reps.app.data.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** A connect request received through a reps://connect deep link. */
data class ConnectRequest(val url: String, val token: String, val name: String)

class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val appVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    val database: AppDatabase by lazy { AppDatabase.build(context) }
    val settings: SettingsRepository by lazy { SettingsRepository(context) }
    val changes = ChangeNotifier()
    val repository: WorkoutRepository by lazy { WorkoutRepository(database, changes) }
    val snapshots: SnapshotRepository by lazy { SnapshotRepository(database, settings, changes, appVersion) }
    val sync: SyncManager by lazy { SyncManager(scope, settings, snapshots, changes) }

    val pendingConnect = MutableStateFlow<ConnectRequest?>(null)

    fun start() {
        scope.launch {
            if (database.exerciseDao().count() == 0) database.exerciseDao().upsertAll(SeedData.exercises())
        }
        sync // start observing changes
    }

    fun vibrate(millis: Long = 40, strong: Boolean = false) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        val amplitude = if (strong) VibrationEffect.DEFAULT_AMPLITUDE else 120
        runCatching { vibrator.vibrate(VibrationEffect.createOneShot(millis, amplitude)) }
    }

    fun vibratePattern(pattern: LongArray) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }
}
