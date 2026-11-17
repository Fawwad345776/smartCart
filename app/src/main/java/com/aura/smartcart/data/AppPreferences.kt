package com.aura.smartcart.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aura.smartcart.util.InputLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

private val Context.dataStore by preferencesDataStore(name = "smartcart_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class WeeklyReminder(
    val enabled: Boolean,
    val days: Set<DayOfWeek>,
    /** Minutes after midnight. */
    val minuteOfDay: Int
)

/** Small app settings stored on the device. */
class AppPreferences(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val THEME = stringPreferencesKey("theme")
        val INPUT_LANGUAGE = stringPreferencesKey("input_language")
        val COUNTRY = stringPreferencesKey("country")
        val WEEKLY_ENABLED = booleanPreferencesKey("weekly_enabled")
        val WEEKLY_DAYS = stringPreferencesKey("weekly_days")
        val WEEKLY_MINUTE = intPreferencesKey("weekly_minute")
    }

    private val data get() = context.dataStore.data

    val onboardingDone: Flow<Boolean> = data.map { it[Keys.ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    val themeMode: Flow<ThemeMode> = data.map { prefs ->
        prefs[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    /** Null until the user picks one; screens then fall back to the app language. */
    val inputLanguage: Flow<InputLanguage?> = data.map { InputLanguage.fromCode(it[Keys.INPUT_LANGUAGE]) }

    suspend fun setInputLanguage(language: InputLanguage) {
        context.dataStore.edit { it[Keys.INPUT_LANGUAGE] = language.code }
    }

    val country: Flow<String?> = data.map { it[Keys.COUNTRY] }

    suspend fun countryOnce(): String? = country.first()

    suspend fun setCountry(code: String) {
        context.dataStore.edit { it[Keys.COUNTRY] = code }
    }

    /** Null until set up for the first time (then filled with the country's default). */
    val weeklyReminder: Flow<WeeklyReminder?> = data.map { prefs ->
        val minute = prefs[Keys.WEEKLY_MINUTE] ?: return@map null
        WeeklyReminder(
            enabled = prefs[Keys.WEEKLY_ENABLED] ?: false,
            days = prefs[Keys.WEEKLY_DAYS].orEmpty().split(',')
                .mapNotNull { it.toIntOrNull()?.let(DayOfWeek::of) }
                .toSet(),
            minuteOfDay = minute
        )
    }

    suspend fun setWeeklyReminder(reminder: WeeklyReminder) {
        context.dataStore.edit {
            it[Keys.WEEKLY_ENABLED] = reminder.enabled
            it[Keys.WEEKLY_DAYS] = reminder.days.sortedBy { d -> d.value }.joinToString(",") { d -> d.value.toString() }
            it[Keys.WEEKLY_MINUTE] = reminder.minuteOfDay
        }
    }
}
