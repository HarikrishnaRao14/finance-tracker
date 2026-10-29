package com.harikrishnarao.financetracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** All themes are deep black with one vivid accent. */
enum class AppTheme(val label: String, val description: String) {
    COBALT("Cobalt", "Electric blue on black"),
    CRIMSON("Crimson", "Cinema red on black"),
    NEBULA("Nebula", "Violet and pink on black"),
}

data class Settings(
    val theme: AppTheme = AppTheme.COBALT,
    val userName: String = "",
    val onboardingDone: Boolean = false,
    val smsEnabled: Boolean = false,
    val parserVersion: Int = 0,
    val checklistDismissed: Boolean = false,
    val lastSyncAt: Long = 0,
    val lastSmsTimestamp: Long = 0,
    val lastSyncRead: Long = 0,
    val lastSyncAdded: Long = 0,
    /** e.g. "2 OTPs, 1 promo, 1 duplicate". */
    val lastSyncSkipped: String = "",
    val incomeOverride: Long = 0,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

/** App settings. Takes the DataStore so tests can give it a temporary file. */
class SettingsStore(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dataStore)

    private object K {
        val theme = stringPreferencesKey("theme")
        val name = stringPreferencesKey("user_name")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val smsEnabled = booleanPreferencesKey("sms_enabled")
        val parserVersion = intPreferencesKey("parser_version")
        val checklistDismissed = booleanPreferencesKey("checklist_dismissed")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastSmsTs = longPreferencesKey("last_sms_ts")
        val read = longPreferencesKey("last_sync_read")
        val added = longPreferencesKey("last_sync_added")
        val skipped = stringPreferencesKey("last_sync_skipped_text")
        val income = longPreferencesKey("income_override")
    }

    val settings: Flow<Settings> = store.data.map { p -> p.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        theme = AppTheme.entries.firstOrNull { it.name == this[K.theme] } ?: AppTheme.COBALT,
        userName = this[K.name] ?: "",
        onboardingDone = this[K.onboarding] ?: false,
        smsEnabled = this[K.smsEnabled] ?: false,
        parserVersion = this[K.parserVersion] ?: 0,
        checklistDismissed = this[K.checklistDismissed] ?: false,
        lastSyncAt = this[K.lastSyncAt] ?: 0,
        lastSmsTimestamp = this[K.lastSmsTs] ?: 0,
        lastSyncRead = this[K.read] ?: 0,
        lastSyncAdded = this[K.added] ?: 0,
        lastSyncSkipped = this[K.skipped] ?: "",
        incomeOverride = this[K.income] ?: 0,
    )

    suspend fun setTheme(theme: AppTheme) = store.edit { it[K.theme] = theme.name }
    suspend fun setUserName(name: String) = store.edit { it[K.name] = name.trim().take(30) }
    suspend fun setOnboardingDone(smsEnabled: Boolean) = store.edit {
        it[K.onboarding] = true
        it[K.smsEnabled] = smsEnabled
    }
    suspend fun setSmsEnabled(on: Boolean) = store.edit { it[K.smsEnabled] = on }
    suspend fun setParserVersion(v: Int) = store.edit { it[K.parserVersion] = v }
    suspend fun setChecklistDismissed(on: Boolean) = store.edit { it[K.checklistDismissed] = on }
    suspend fun setIncomeOverride(paise: Long) = store.edit { it[K.income] = paise }
    suspend fun resetSmsCursor() = store.edit { it[K.lastSmsTs] = 0 }

    suspend fun recordSync(at: Long, lastSmsTs: Long, read: Int, added: Int, skipped: String) = store.edit {
        it[K.lastSyncAt] = at
        it[K.lastSmsTs] = lastSmsTs
        it[K.read] = read.toLong()
        it[K.added] = added.toLong()
        it[K.skipped] = skipped
    }
}
