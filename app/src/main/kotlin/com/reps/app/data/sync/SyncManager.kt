package com.reps.app.data.sync

import com.reps.app.data.prefs.SettingsRepository
import com.reps.app.data.repo.ChangeNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
}

/**
 * Keeps the server copy of the data fresh: every change marks the data dirty and schedules a
 * debounced push; the app also pushes on foreground and right after a workout is finished.
 */
@OptIn(FlowPreview::class)
class SyncManager(
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val snapshots: SnapshotRepository,
    private val changes: ChangeNotifier,
    private val client: SyncClient = SyncClient()
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    init {
        changes.changes.onEach { settings.markDirty(true) }.launchIn(scope)
        changes.changes.debounce(8_000).onEach { pushIfEnabled() }.launchIn(scope)
    }

    fun pushIfEnabled() {
        scope.launch {
            val s = settings.current()
            if (s.syncConfigured && s.autoSync) runCatching { push() }
        }
    }

    fun pushIfDirty() {
        scope.launch {
            val s = settings.current()
            if (s.syncConfigured && s.autoSync && s.syncDirty) runCatching { push() }
        }
    }

    /** Pushes a full snapshot now. Throws [SyncException] on failure. */
    suspend fun push(): PushResult = mutex.withLock {
        val s = settings.current()
        if (!s.syncConfigured) throw SyncException("Sync is not set up")
        _state.value = SyncState.Syncing
        try {
            val result = client.push(s.syncUrl, s.syncToken, snapshots.exportJson())
            settings.recordSyncSuccess(System.currentTimeMillis())
            result
        } catch (e: SyncException) {
            settings.recordSyncError(e.message ?: "Sync failed")
            throw e
        } finally {
            _state.value = SyncState.Idle
        }
    }

    suspend fun testConnection(url: String, token: String): ServerInfo = client.info(url.trim().trimEnd('/'), token.trim())

    /** Downloads the latest server snapshot and replaces local data with it. Returns null when the server holds nothing. */
    suspend fun restoreLatest(): RestoreSummary? = mutex.withLock {
        val s = settings.current()
        if (!s.syncConfigured) throw SyncException("Sync is not set up")
        _state.value = SyncState.Syncing
        try {
            val text = client.latest(s.syncUrl, s.syncToken) ?: return null
            val snapshot = try { snapshots.parse(text) } catch (e: Exception) { throw SyncException("Backup file is not readable", e) }
            val summary = snapshots.restore(snapshot)
            settings.recordSyncSuccess(System.currentTimeMillis())
            summary
        } finally {
            _state.value = SyncState.Idle
        }
    }
}
