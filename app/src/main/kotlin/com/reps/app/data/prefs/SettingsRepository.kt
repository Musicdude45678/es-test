package com.reps.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.reps.app.data.model.ThemeMode
import com.reps.app.data.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    val unit: WeightUnit = WeightUnit.KG,
    val defaultBarWeightKg: Double = 20.0,
    val weightIncrement: Double = 2.5,
    val restTimerSeconds: Int = 90,
    val autoRestTimer: Boolean = true,
    val keepScreenOn: Boolean = true,
    val haptics: Boolean = true,
    val theme: ThemeMode = ThemeMode.DARK,
    val syncUrl: String = "",
    val syncToken: String = "",
    val autoSync: Boolean = true,
    val lastSyncAt: Long = 0L,
    val lastSyncError: String = "",
    val syncDirty: Boolean = false,
    val onboarded: Boolean = false
) {
    val syncConfigured: Boolean get() = syncUrl.isNotBlank() && syncToken.isNotBlank()
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val unit = stringPreferencesKey("unit")
        val barKg = doublePreferencesKey("default_bar_kg")
        val increment = doublePreferencesKey("increment")
        val restSeconds = intPreferencesKey("rest_seconds")
        val autoRest = booleanPreferencesKey("auto_rest")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val haptics = booleanPreferencesKey("haptics")
        val theme = stringPreferencesKey("theme")
        val syncUrl = stringPreferencesKey("sync_url")
        val syncToken = stringPreferencesKey("sync_token")
        val autoSync = booleanPreferencesKey("auto_sync")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastSyncError = stringPreferencesKey("last_sync_error")
        val syncDirty = booleanPreferencesKey("sync_dirty")
        val onboarded = booleanPreferencesKey("onboarded")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            unit = p[Keys.unit]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: WeightUnit.KG,
            defaultBarWeightKg = p[Keys.barKg] ?: 20.0,
            weightIncrement = p[Keys.increment] ?: 2.5,
            restTimerSeconds = p[Keys.restSeconds] ?: 90,
            autoRestTimer = p[Keys.autoRest] ?: true,
            keepScreenOn = p[Keys.keepScreenOn] ?: true,
            haptics = p[Keys.haptics] ?: true,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            syncUrl = p[Keys.syncUrl] ?: "",
            syncToken = p[Keys.syncToken] ?: "",
            autoSync = p[Keys.autoSync] ?: true,
            lastSyncAt = p[Keys.lastSyncAt] ?: 0L,
            lastSyncError = p[Keys.lastSyncError] ?: "",
            syncDirty = p[Keys.syncDirty] ?: false,
            onboarded = p[Keys.onboarded] ?: false
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setUnit(unit: WeightUnit) = context.dataStore.edit { it[Keys.unit] = unit.name }
    suspend fun setDefaultBarWeightKg(kg: Double) = context.dataStore.edit { it[Keys.barKg] = kg }
    suspend fun setWeightIncrement(kg: Double) = context.dataStore.edit { it[Keys.increment] = kg }
    suspend fun setRestTimerSeconds(seconds: Int) = context.dataStore.edit { it[Keys.restSeconds] = seconds }
    suspend fun setAutoRestTimer(enabled: Boolean) = context.dataStore.edit { it[Keys.autoRest] = enabled }
    suspend fun setKeepScreenOn(enabled: Boolean) = context.dataStore.edit { it[Keys.keepScreenOn] = enabled }
    suspend fun setHaptics(enabled: Boolean) = context.dataStore.edit { it[Keys.haptics] = enabled }
    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setAutoSync(enabled: Boolean) = context.dataStore.edit { it[Keys.autoSync] = enabled }
    suspend fun setOnboarded() = context.dataStore.edit { it[Keys.onboarded] = true }

    suspend fun setSyncConfig(url: String, token: String) = context.dataStore.edit {
        it[Keys.syncUrl] = url.trim().trimEnd('/')
        it[Keys.syncToken] = token.trim()
        it[Keys.lastSyncError] = ""
    }

    suspend fun clearSyncConfig() = context.dataStore.edit {
        it.remove(Keys.syncUrl); it.remove(Keys.syncToken); it.remove(Keys.lastSyncAt); it.remove(Keys.lastSyncError)
    }

    suspend fun markDirty(dirty: Boolean) = context.dataStore.edit { it[Keys.syncDirty] = dirty }

    suspend fun recordSyncSuccess(at: Long) = context.dataStore.edit {
        it[Keys.lastSyncAt] = at
        it[Keys.lastSyncError] = ""
        it[Keys.syncDirty] = false
    }

    suspend fun recordSyncError(message: String) = context.dataStore.edit { it[Keys.lastSyncError] = message }
}
