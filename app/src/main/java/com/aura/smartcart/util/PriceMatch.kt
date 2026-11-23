package com.aura.smartcart.util

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Helpers for comparing prices of the same item across bills:
 * item name matching, quantity matching, geohash cells for "nearby" queries, and distance.
 */
object PriceMatch {

    private val QTY_IN_NAME = Regex("^\\d+(?:\\.\\d+)?[a-z]*$")
    private val UNITS = mapOf(
        "kg" to "kg", "kgs" to "kg", "kilo" to "kg", "kilogram" to "kg",
        "g" to "g", "gm" to "g", "gms" to "g", "gram" to "g", "grams" to "g",
        "l" to "l", "ltr" to "l", "lt" to "l", "litre" to "l", "liter" to "l",
        "ml" to "ml", "pc" to "pc", "pcs" to "pc", "piece" to "pc", "pack" to "pack", "pk" to "pack",
        "dz" to "dozen", "dozen" to "dozen", "darjan" to "dozen", "packet" to "pack"
    )

    /** Words of an item name: lower case, letters/digits only, quantities like "5kg" dropped. */
    fun words(name: String): Set<String> =
        Digits.toAscii(name).lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}.]+"))
            .map { it.trim('.') }
            .filter { it.length >= 2 && !QTY_IN_NAME.matches(it) }
            .toSet()

    /** One key per item name, used to group the same item. */
    fun key(name: String): String = words(name).sorted().joinToString(" ")

    /**
     * Same item? Every word of the shorter name must appear in the longer one,
     * so "Chawal" matches "Chawal Basmati" and "Rice Basmati 5kg" matches "Basmati Rice".
     */
    fun sameItem(a: String, b: String): Boolean {
        val x = words(a)
        val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return false
        return if (x.size <= y.size) y.containsAll(x) else x.containsAll(y)
    }

    /** "5 kg", "5kg", "5 kilo" -> "5kg"; "2" -> "2"; "" -> "". */
    fun quantity(text: String): String {
        val t = Digits.toAscii(text).lowercase(Locale.ROOT).replace(" ", "")
        val m = Regex("^(\\d+(?:\\.\\d+)?)([a-z]*)$").find(t) ?: return t
        val number = m.groupValues[1].toDouble().let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
        val unit = UNITS[m.groupValues[2]] ?: m.groupValues[2]
        return number + unit
    }

    /** Prices are only compared when both quantities are the same (or both unknown). */
    fun sameQuantity(a: String, b: String): Boolean = quantity(a) == quantity(b)

    // ---------- geohash ----------

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    /** Geohash of [precision] characters. 5 chars ≈ 4.9 x 4.9 km, 4 chars ≈ 39 x 20 km. */
    fun geohash(lat: Double, lng: Double, precision: Int): String {
        var latMin = -90.0; var latMax = 90.0
        var lngMin = -180.0; var lngMax = 180.0
        val sb = StringBuilder()
        var bit = 0; var ch = 0; var even = true
        while (sb.length < precision) {
            if (even) {
                val mid = (lngMin + lngMax) / 2
                if (lng >= mid) { ch = ch or (1 shl (4 - bit)); lngMin = mid } else lngMax = mid
            } else {
                val mid = (latMin + latMax) / 2
                if (lat >= mid) { ch = ch or (1 shl (4 - bit)); latMin = mid } else latMax = mid
            }
            even = !even
            if (bit < 4) bit++ else { sb.append(BASE32[ch]); bit = 0; ch = 0 }
        }
        return sb.toString()
    }

    /** The cell of the point and the 8 cells around it (for "within X km" queries). */
    fun cellsAround(lat: Double, lng: Double, precision: Int): List<String> {
        // Cell size in degrees for this precision.
        val lngBits = (precision * 5 + 1) / 2
        val latBits = precision * 5 / 2
        val dLat = 180.0 / 2.0.pow(latBits)
        val dLng = 360.0 / 2.0.pow(lngBits)
        val cells = LinkedHashSet<String>()
        for (i in -1..1) for (j in -1..1) {
            val la = (lat + i * dLat).coerceIn(-89.999, 89.999)
            var ln = lng + j * dLng
            if (ln > 180) ln -= 360
            if (ln < -180) ln += 360
            cells += geohash(la, ln, precision)
        }
        return cells.toList()
    }

    /** Distance in km between two points. */
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
