package com.harikrishnarao.financetracker.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class AppTheme(val label: String, val description: String) {
    FOREST("Forest", "Calm green on soft white"),
    MIDNIGHT("Midnight", "Dark, easy on the eyes at night"),
    SAFFRON("Saffron", "Warm cream with a deep orange"),
}

data class Settings(
    val theme: AppTheme = AppTheme.FOREST,
    val followSystemDark: Boolean = true,
    val onboardingDone: Boolean = false,
    val smsEnabled: Boolean = false,
    val lastSyncAt: Long = 0,
    val lastSmsTimestamp: Long = 0,
    val lastSyncRead: Long = 0,
    val lastSyncSkipped: Long = 0,
    val lastSyncAdded: Long = 0,
    val incomeOverride: Long = 0,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val followSystem = booleanPreferencesKey("follow_system_dark")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val smsEnabled = booleanPreferencesKey("sms_enabled")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastSmsTs = longPreferencesKey("last_sms_ts")
        val read = longPreferencesKey("last_sync_read")
        val skipped = longPreferencesKey("last_sync_skipped")
        val added = longPreferencesKey("last_sync_added")
        val income = longPreferencesKey("income_override")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        theme = AppTheme.entries.firstOrNull { it.name == this[K.theme] } ?: AppTheme.FOREST,
        followSystemDark = this[K.followSystem] ?: true,
        onboardingDone = this[K.onboarding] ?: false,
        smsEnabled = this[K.smsEnabled] ?: false,
        lastSyncAt = this[K.lastSyncAt] ?: 0,
        lastSmsTimestamp = this[K.lastSmsTs] ?: 0,
        lastSyncRead = this[K.read] ?: 0,
        lastSyncSkipped = this[K.skipped] ?: 0,
        lastSyncAdded = this[K.added] ?: 0,
        incomeOverride = this[K.income] ?: 0,
    )

    suspend fun setTheme(theme: AppTheme) = context.dataStore.edit { it[K.theme] = theme.name }
    suspend fun setFollowSystemDark(on: Boolean) = context.dataStore.edit { it[K.followSystem] = on }
    suspend fun setOnboardingDone(smsEnabled: Boolean) = context.dataStore.edit {
        it[K.onboarding] = true
        it[K.smsEnabled] = smsEnabled
    }
    suspend fun setSmsEnabled(on: Boolean) = context.dataStore.edit { it[K.smsEnabled] = on }
    suspend fun setIncomeOverride(paise: Long) = context.dataStore.edit { it[K.income] = paise }

    suspend fun recordSync(at: Long, lastSmsTs: Long, read: Int, skipped: Int, added: Int) = context.dataStore.edit {
        it[K.lastSyncAt] = at
        it[K.lastSmsTs] = lastSmsTs
        it[K.read] = read.toLong()
        it[K.skipped] = skipped.toLong()
        it[K.added] = added.toLong()
    }
}
