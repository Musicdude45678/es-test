package com.reps.app.data.repo

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Emits whenever user data changes, so the sync layer can schedule a backup. */
class ChangeNotifier {
    private val _changes = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    val changes: SharedFlow<Long> = _changes.asSharedFlow()
    fun notifyChanged() { _changes.tryEmit(System.currentTimeMillis()) }
}
