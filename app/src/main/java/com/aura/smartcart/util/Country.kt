package com.aura.smartcart.util

import android.content.Context
import android.telephony.TelephonyManager
import java.time.DayOfWeek
import java.util.Currency
import java.util.Locale

object Country {

    private val ARAB_COUNTRIES = setOf(
        "SA", "AE", "QA", "KW", "BH", "OM", "YE", "IQ", "JO", "LB", "SY", "PS",
        "EG", "LY", "TN", "DZ", "MA", "SD", "MR", "SO", "DJ", "KM"
    )
    private val GULF = setOf("SA", "AE", "QA", "KW", "BH", "OM")
    private val SOUTH_ASIA = setOf("PK", "IN", "BD", "NP", "LK")

    /** ISO country of the user: SIM, then network, then phone locale. */
    fun detect(context: Context): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val fromSim = tm?.simCountryIso?.takeIf { it.length == 2 }
        val fromNetwork = tm?.networkCountryIso?.takeIf { it.length == 2 }
        val fromLocale = Locale.getDefault().country.takeIf { it.length == 2 }
        return (fromSim ?: fromNetwork ?: fromLocale ?: "US").uppercase(Locale.ROOT)
    }

    /** India opens in Hindi, Arab countries in Arabic, everyone else in English. */
    fun defaultAppLanguage(country: String): AppLanguage = when {
        country == "IN" -> AppLanguage.HINDI
        country in ARAB_COUNTRIES -> AppLanguage.ARABIC
        else -> AppLanguage.ENGLISH
    }

    fun currencyCode(country: String): String = try {
        Currency.getInstance(Locale("", country)).currencyCode
    } catch (_: Exception) {
        "USD"
    }

    /**
     * Typical grocery-shopping habits per region: which day people usually go to the market
     * and at what time (minutes after midnight). Used to pre-fill the weekly reminders.
     */
    fun defaultMarketSchedule(country: String): Pair<Set<DayOfWeek>, Int> = when {
        country in GULF -> setOf(DayOfWeek.FRIDAY) to 19 * 60
        country in ARAB_COUNTRIES -> setOf(DayOfWeek.FRIDAY) to 18 * 60
        country in SOUTH_ASIA -> setOf(DayOfWeek.SUNDAY) to 18 * 60
        else -> setOf(DayOfWeek.SATURDAY) to 10 * 60
    }
}
