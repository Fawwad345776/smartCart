package com.aura.smartcart.util

import com.aura.smartcart.util.ItemTextParser.capitalizeFirst
import java.util.Locale

/** One bill line heard by voice. [price] is the line total as plain text ("1450", "99.5"). */
data class BillVoiceItem(val name: String, val quantity: String, val price: String)

/**
 * Reads bill lines from speech: item name, quantity and price, in any of the app's languages.
 *
 *  "chawal 5 kilo 1450, doodh 2 packet 360 rupay"  -> Chawal | 5 kilo | 1450,  Doodh | 2 packet | 360
 *  "anday ek darjan 300 ke"                         -> Anday | 1 darjan | 300
 *  "2 bread 300"                                    -> Bread | 2 | 300
 *  "cheeni dedh kilo teen sau"                      -> Cheeni | 1½ kilo | 300
 *  fast, no "and":  "chawal 1450 doodh 360"         -> Chawal | | 1450,  Doodh | | 360
 *
 * Rules: a number with a unit is the quantity; a number with a currency word ("rupay", "rs",
 * "ريال", "रुपये"…) or after "price/daam/qeemat" is the price; otherwise, a lone number after the
 * name is the price and one before the name is the count.
 */
object BillVoiceParser {

    private val CURRENCY: Set<String> = setOf(
        "rs", "rs.", "rupay", "rupaye", "rupees", "rupee", "rupye", "rupiya", "rupaiya", "rupay.", "pkr", "inr",
        "روپے", "روپیہ", "روپئے", "روپیے", "रुपये", "रुपए", "रुपया", "रु",
        "riyal", "riyals", "rial", "sar", "ريال", "ريالات", "dirham", "dirhams", "aed", "درهم", "دراهم",
        "dinar", "dinars", "دينار", "qar", "kwd", "omr", "bhd", "dollar", "dollars", "usd", "$", "taka", "tk", "pound", "£", "€", "euro"
    )

    /** Words that announce a price ("daam 180", "price 99"). */
    private val PRICE_WORDS: Set<String> = setOf(
        "price", "rate", "cost", "qeemat", "keemat", "qimat", "kimat", "daam", "dam", "paise", "paisay",
        "قیمت", "دام", "पैसे", "दाम", "कीमत", "क़ीमत", "سعر", "بسعر", "ثمن", "بـ"
    )

    /** Small linking words around prices ("300 ke", "for 50", "at 20"). */
    private val LINKERS: Set<String> = setOf(
        "ka", "ke", "ki", "for", "at", "@", "of", "mein", "main", "me", "wala", "wali", "wale",
        "का", "के", "की", "में", "वाला", "کا", "کے", "کی", "میں", "والا", "ب", "مقابل"
    )

    private val MULTIPLIERS: Map<String, Int> = mapOf(
        "hundred" to 100, "sau" to 100, "so" to 100, "सौ" to 100, "سو" to 100, "مائة" to 100, "مية" to 100, "ميه" to 100,
        "thousand" to 1000, "hazar" to 1000, "hazaar" to 1000, "hazār" to 1000, "हज़ार" to 1000, "हजार" to 1000,
        "ہزار" to 1000, "ألف" to 1000, "الف" to 1000, "k" to 1000
    )

    fun parse(text: String): List<BillVoiceItem> =
        ItemTextParser.normalize(text)
            .replace(Regex("([$£€])(\\d)"), "$1 $2")
            .split(ItemTextParser.SEPARATORS)
            .flatMap { parsePiece(it) }

    private class Num(val value: Double, val display: String)

    private class Cur {
        val name = mutableListOf<String>()
        var qty: String? = null
        var qtyAfterName = false
        var price: Double? = null
        val bare = mutableListOf<Pair<Num, Boolean>>() // number, came before the name?
        fun isEmpty() = name.isEmpty() && qty == null && price == null && bare.isEmpty()
    }

    private fun parsePiece(piece: String): List<BillVoiceItem> {
        val words = piece.trim().split(Regex("\\s+"))
            .map { it.trim('.', ':', '!', '?', '"', '\'', '(', ')', '؟', '।', '/', '-') }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        val done = mutableListOf<Cur>()
        var cur = Cur()
        var expectPrice = false
        var i = 0

        fun push() {
            if (!cur.isEmpty()) done += cur
            cur = Cur()
        }

        while (i < words.size) {
            val lower = words[i].lowercase(Locale.ROOT)

            if (lower in CURRENCY || lower in PRICE_WORDS) {
                expectPrice = true; i++; continue
            }

            val number = readNumber(words, i)
            if (number != null) {
                val (num, next) = number
                var j = next
                when {
                    // quantity: number + unit
                    j < words.size && ItemTextParser.isUnit(words[j]) -> {
                        val q = "${num.display} ${words[j]}"
                        j++
                        if (cur.qty != null || cur.price != null) push()
                        cur.qty = q
                        cur.qtyAfterName = cur.name.isNotEmpty()
                    }
                    // price: currency word before or after, or announced by "price/daam"
                    expectPrice || (j < words.size && words[j].lowercase(Locale.ROOT) in CURRENCY) -> {
                        if (j < words.size && words[j].lowercase(Locale.ROOT) in CURRENCY) j++
                        if (cur.price != null) push()
                        cur.price = num.value
                        expectPrice = false
                    }
                    else -> {
                        if (cur.price != null) push()
                        cur.bare += num to cur.name.isEmpty()
                    }
                }
                i = j
                continue
            }

            if (lower in ItemTextParser.FILLERS || lower in LINKERS) { i++; continue }

            // A name word after this item's price/numbers starts the next item.
            val itemHasNumbersAfterName = cur.name.isNotEmpty() &&
                (cur.bare.any { !it.second } || cur.qtyAfterName)
            if (cur.price != null || itemHasNumbersAfterName) push()
            cur.name += words[i]
            i++
        }
        push()

        val out = mutableListOf<BillVoiceItem>()
        for (c in done) {
            var qty = c.qty
            var price = c.price
            val bare = c.bare
            if (price == null && bare.isNotEmpty()) {
                if (bare.size >= 2) {
                    if (qty == null) qty = bare.first().first.display
                    price = bare.last().first.value
                } else {
                    val (n, beforeName) = bare.first()
                    if (beforeName && qty == null && c.name.isNotEmpty()) qty = n.display else price = n.value
                }
            } else if (price != null && qty == null && bare.isNotEmpty()) {
                qty = bare.first().first.display
            }

            val name = c.name.joinToString(" ").trim()
            if (name.isEmpty() || name.none { it.isLetter() }) {
                // "… 360 rupay" said after a pause: give the price to the previous item.
                val last = out.lastOrNull()
                if (last != null && price != null && last.price.isBlank()) {
                    out[out.lastIndex] = last.copy(price = plain(price))
                }
                continue
            }
            out += BillVoiceItem(name.capitalizeFirst(), qty.orEmpty(), price?.let(::plain).orEmpty())
        }
        return out
    }

    /**
     * Reads "1450", "99.50", "teen sau", "do hazaar paanch sau", "dedh sau", "½".
     * Returns the number and the index after it.
     */
    private fun readNumber(words: List<String>, start: Int): Pair<Num, Int>? {
        var total = 0.0
        var current: Double? = null
        var display: String? = null
        var i = start
        var used = false
        while (i < words.size) {
            val w = words[i].lowercase(Locale.ROOT)
            val mult = MULTIPLIERS[w]
            if (mult != null && (used || w != "so" && w != "k")) {
                total += (current ?: 1.0) * mult
                current = null
                used = true
                display = null
                i++
                continue
            }
            // Stop before a second plain number unless a multiplier joins them ("5 kilo 1450").
            if (current != null) break
            val text = ItemTextParser.numberOf(words[i], words.getOrNull(i + 1)) ?: break
            current = valueOf(text) ?: break
            display = text
            used = true
            i++
        }
        if (!used) return null
        val value = total + (current ?: 0.0)
        val shown = if (total == 0.0 && display != null) display else plain(value)
        return Num(value, shown) to i
    }

    private fun valueOf(text: String): Double? {
        val fractions = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75)
        val last = text.lastOrNull() ?: return null
        val frac = fractions[last]
        return if (frac != null) {
            val whole = text.dropLast(1)
            (if (whole.isEmpty()) 0.0 else whole.toDoubleOrNull() ?: return null) + frac
        } else if (text.contains('/')) {
            val (a, b) = text.split('/').map { it.toDoubleOrNull() ?: return null }
            if (b == 0.0) null else a / b
        } else {
            text.toDoubleOrNull()
        }
    }

    private fun plain(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
}
