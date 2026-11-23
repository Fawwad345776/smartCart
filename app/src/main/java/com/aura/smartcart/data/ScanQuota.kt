package com.aura.smartcart.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.YearMonth

private val Context.quotaStore by preferencesDataStore(name = "scan_quota")

/** Kinds of scan with their free monthly limit. Pro has no limit. */
enum class ScanKind(val freeLimit: Int) {
    BILL_PHOTO(10),
    BILL_VIDEO(3),
    LIST(3)
}

/**
 * Counts free scans per calendar month; the count starts again on the 1st of every month.
 * [isPro] is checked on every scan, so Pro users are never limited or counted.
 */
class ScanQuota(private val context: Context, private val isPro: () -> Boolean) {

    private val monthKey = stringPreferencesKey("month")
    private fun countKey(kind: ScanKind) = intPreferencesKey("used_${kind.name.lowercase()}")
    private fun thisMonth() = YearMonth.now().toString()

    /** Scans used this month per kind. */
    val usedThisMonth: Flow<Map<ScanKind, Int>> = context.quotaStore.data.map { prefs -> counts(prefs) }

    suspend fun canUse(kind: ScanKind): Boolean =
        isPro() || (usedThisMonth.first()[kind] ?: 0) < kind.freeLimit

    /** Call once a scan really happened (photo taken / video started). */
    suspend fun recordUse(kind: ScanKind) {
        if (isPro()) return
        context.quotaStore.edit { prefs ->
            val month = thisMonth()
            if (prefs[monthKey] != month) {
                ScanKind.entries.forEach { prefs.remove(countKey(it)) }
                prefs[monthKey] = month
            }
            prefs[countKey(kind)] = (prefs[countKey(kind)] ?: 0) + 1
        }
    }

    private fun counts(prefs: Preferences): Map<ScanKind, Int> {
        val current = prefs[monthKey] == thisMonth()
        return ScanKind.entries.associateWith { if (current) prefs[countKey(it)] ?: 0 else 0 }
    }
}
