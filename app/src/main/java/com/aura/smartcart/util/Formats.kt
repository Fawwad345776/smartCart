package com.aura.smartcart.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

object Months {
    private val KEY = DateTimeFormatter.ofPattern("yyyy-MM")

    fun keyOf(millis: Long): String =
        YearMonth.from(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())).format(KEY)

    fun currentKey(): String = YearMonth.now().format(KEY)

    fun label(monthKey: String, locale: Locale = Locale.getDefault()): String = try {
        YearMonth.parse(monthKey, KEY).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    } catch (_: Exception) {
        monthKey
    }

    /** Days left in the current month, including today. */
    fun daysLeftInMonth(): Int {
        val today = LocalDate.now()
        return today.lengthOfMonth() - today.dayOfMonth + 1
    }

    fun startOfCurrentMonthMillis(): Long =
        YearMonth.now().atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

object Money {
    fun format(amount: Double, currencyCode: String): String {
        val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
        try {
            nf.currency = Currency.getInstance(currencyCode)
        } catch (_: Exception) {
        }
        nf.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        nf.minimumFractionDigits = 0
        return nf.format(amount)
    }

    /** Parses user/OCR input like "1,250.50" or "١٢٥٠" into a number. */
    fun parse(text: String): Double? {
        val normalized = Digits.toAscii(text).replace(",", "").replace(Regex("[^0-9.]"), "")
        return normalized.toDoubleOrNull()
    }
}

object Dates {
    fun dateTime(millis: Long): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun date(millis: Long): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun time(minutesOfDay: Int): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .format(java.time.LocalTime.of(minutesOfDay / 60, minutesOfDay % 60))
}

object Digits {
    /** Converts Arabic-Indic, Persian/Urdu and Devanagari digits to 0-9. */
    fun toAscii(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '०'..'९' -> '0' + (c - '०')
                    '٫' -> '.'
                    '٬' -> ','
                    else -> c
                }
            )
        }
    }
}
