package com.aura.smartcart.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One item line read from a printed bill. [price] is the line total. */
data class ReceiptItem(val name: String, val quantity: String, val price: Double)

data class ReceiptResult(
    val store: String,
    /** yyyy-MM-dd or "" */
    val date: String,
    val items: List<ReceiptItem>,
    val total: Double
)

/**
 * Turns OCR rows of a receipt into Item / Qty / Price columns.
 * Each row is the text of one printed line, left to right (pieces that OCR read separately on the
 * same line are joined first). Handles the usual layouts:
 *
 *   Rice Basmati 5kg        1,450.00        -> Rice Basmati | 5 kg | 1450
 *   Milk Pack 1L   2   180.00   360.00      -> Milk Pack 1L | 2    | 360
 *   Sugar                                    (name on its own line)
 *     2 x 160.00            320.00          -> Sugar        | 2    | 320
 *   TOTAL                   2,130.00        -> total
 */
object ReceiptParser {

    private val TOTAL_WORDS = Regex(
        "(?i)\\b(grand\\s*total|net\\s*total|net\\s*amount|total\\s*amount|total\\s*payable|amount\\s*payable|" +
            "bill\\s*amount|total|payable|net)\\b|कुल|कुल\\s*राशि|کل|ٹوٹل|المجموع|الإجمالي|الاجمالي|المبلغ"
    )
    private val SUB_TOTAL = Regex("(?i)\\bsub\\s*-?\\s*total")
    private val SKIP_WORDS = Regex(
        "(?i)\\b(sub\\s*-?\\s*total|subtotal|tax|gst|vat|sst|fed|pst|service|discount|disc|savings|saved|cash|change|" +
            "balance|tender|tendered|paid|card|visa|master|round|rounding|invoice|receipt|bill\\s*no|inv|ntn|strn|tel|phone|" +
            "ph|mob|cell|fax|www|http|email|cashier|counter|till|terminal|pos|thank|thanks|visit|again|qty\\s*total|" +
            "items?\\s*count|no\\s*of\\s*items|time|date|token|order|table|guest|customer)\\b|ضریبة|ضريبة|خصم|نقد|الباقي"
    )
    private val HEADER_WORDS = setOf(
        "item", "items", "description", "desc", "product", "particulars", "qty", "quantity", "rate", "price",
        "amount", "amt", "total", "s.no", "sno", "sr", "#", "no", "unit", "mrp", "value", "disc", "net", "pcs"
    )
    private val CURRENCY = Regex("(?i)^(rs\\.?|pkr|inr|₹|sar|aed|riyal|qar|kwd|omr|bhd|usd|\\$|£|€|dhs?|د\\.إ|ر\\.س|روپے|रु\\.?)$")
    private val PRICE_TOKEN = Regex("^-?\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,3})?$|^-?\\d+(?:\\.\\d{1,3})?$")
    private val TIMES = Regex("^[x×*@]$", RegexOption.IGNORE_CASE)
    private val QTY_X = Regex("^(\\d+(?:\\.\\d+)?)[x×*]$", RegexOption.IGNORE_CASE) // "2x"
    private val UNIT_QTY = Regex(
        "(?i)^(\\d+(?:\\.\\d+)?)\\s*(kg|kgs|g|gm|gms|gram|grams|l|ltr|lt|litre|liter|ml|pc|pcs|pack|pk|dz|dozen)$"
    )
    private val DATE_PATTERNS = listOf(
        Regex("(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})") to "dmy",
        Regex("(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})") to "ymd",
        Regex("(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2})\\b") to "dmy2"
    )

    fun parse(rows: List<String>): ReceiptResult {
        val clean = rows.map { fixOcrNumbers(Digits.toAscii(it).replace('\t', ' ')).trim() }
            .filter { it.isNotEmpty() && !FILE_NAME.containsMatchIn(it) }

        var store = ""
        var date = ""
        var total = 0.0
        val items = mutableListOf<ReceiptItem>()
        var pendingName: String? = null

        for ((index, row) in clean.withIndex()) {
            if (date.isEmpty()) date = findDate(row)

            val tokens = row.split(Regex("\\s+")).filter { it.isNotEmpty() }
                // barcodes / phone numbers are not prices
                .filterNot { t -> t.count { it.isDigit() } >= 8 && !t.contains('.') }
            val numbers = trailingNumbers(tokens)
            val nameTokens = tokens.subList(0, tokens.size - numbers.tokenCount)
            val nameText = nameTokens.joinToString(" ").trim().trim(':', '-', '.', '*')
            val hasLetters = nameText.any { it.isLetter() }

            // Totals and other footer/header lines. "Sub Total" is not the bill total (discount/tax come after).
            if (SUB_TOTAL.containsMatchIn(row)) { pendingName = null; continue }
            if (TOTAL_WORDS.containsMatchIn(row) && !SKIP_WORDS.containsMatchIn(row.replace(TOTAL_WORDS, " "))) {
                numbers.values.lastOrNull()?.value?.let { if (it > total) total = it }
                pendingName = null
                continue
            }
            if (SKIP_WORDS.containsMatchIn(row)) { pendingName = null; continue }
            if (isHeader(tokens)) continue

            if (numbers.values.isEmpty()) {
                // A name with its numbers on the next line, or the shop name at the top.
                if (hasLetters) {
                    // A name whose next line is "1 x 720.00   720.00" is an item, even at the top
                    // (a video frame often starts in the middle of the bill).
                    val next = clean.getOrNull(index + 1)
                    val nextIsQtyLine = next != null && next.none { it.isLetter() && it !in "xX×" } && next.any { it.isDigit() }
                    if (!nextIsQtyLine && items.isEmpty() && store.isEmpty() && index < 4 && pendingName == null) {
                        store = nameText
                    } else {
                        pendingName = nameText
                    }
                }
                continue
            }

            val (qtyFromNumbers, price) = columns(numbers.values, numbers.hadTimes)
            if (price <= 0.0) continue
            // A lone single digit ("Delete 8", "P 6": keyboard keys, page numbers) is not a price:
            // bills print "8.00" or at least two digits.
            val last = numbers.values.last()
            if (numbers.values.size == 1 && last.decimals == 0 && last.value < 10) { pendingName = null; continue }

            if (!hasLetters) {
                // "2 x 160.00  320.00" under a name line
                val name = pendingName ?: continue
                items += ReceiptItem(name.titleCaseIfShouting(), qtyFromNumbers, price)
                pendingName = null
                continue
            }

            var name = nameText
            var qty = qtyFromNumbers
            // "Rice 5kg" with no separate count: move "5 kg" into the Qty column.
            if (qty.isEmpty()) {
                val last = nameTokens.lastOrNull()
                val m = last?.let { UNIT_QTY.matchEntire(it) }
                    ?: nameTokens.takeLast(2).joinToString(" ").let { UNIT_QTY.matchEntire(it) }
                if (m != null) {
                    qty = "${m.groupValues[1]} ${m.groupValues[2].lowercase(Locale.ROOT)}"
                    val drop = if (last != null && UNIT_QTY.matches(last)) 1 else 2
                    name = nameTokens.dropLast(drop).joinToString(" ").trim()
                }
            }
            if (pendingName != null) {
                name = "$pendingName $name".trim()
                pendingName = null
            }
            if (name.none { it.isLetter() }) continue
            if (store.isEmpty() && items.isEmpty() && index == 0) store = name
            items += ReceiptItem(name.titleCaseIfShouting(), qty, price)
        }

        if (total == 0.0) total = items.sumOf { it.price }
        return ReceiptResult(store.titleCaseIfShouting(), date, items, total)
    }

    /** A file name or web address (e.g. a receipt shown on a computer screen), never a bill line. */
    private val FILE_NAME = Regex("(?i)\\w\\.(pdf|png|jpe?g|docx?|xlsx?|html?)\\b|_\\w+\\.\\w|https?://")

    /**
     * Repairs the usual OCR slips in numbers, mostly from video frames:
     *   "720:00" / "720;00" -> "720.00"      "9, 025.00" -> "9,025.00"     "9,000. 00" / "165 .00" -> "9,000.00" / "165.00"
     *   "620,00" -> "620.00"                 "2x165.00" / "2 x165" -> "2 x 165.00" / "2 x 165"
     */
    internal fun fixOcrNumbers(row: String): String = row
        .replace(Regex("(\\d)\\s*[:;]\\s*(\\d{2})(?!\\d)"), "$1.$2")
        .replace(Regex("(?i)(\\d)[:;](\\d)(?=\\s*(?:l|ltr|kg|g|ml)\\b)"), "$1.$2") // "1:5L" -> "1.5L"
        .replace(Regex("(\\d),\\s+(\\d{3})(?!\\d)"), "$1,$2")
        .replace(Regex("(\\d)\\s*\\.\\s+(\\d{2})(?!\\d)"), "$1.$2")
        .replace(Regex("(\\d)\\s+\\.(\\d{2})(?!\\d)"), "$1.$2")
        .replace(Regex("(?<![\\d,])(\\d{1,3}),(\\d{2})(?![\\d,])"), "$1.$2")
        .replace(Regex("(?i)(?<![\\w.])(\\d{1,3})\\s*([x×*])\\s*(?=\\d)"), "$1 x ")

    /** A number on a bill and how many digits it had after the point ("160.00" -> 2, "1.250" -> 3). */
    private class NumTok(val value: Double, val decimals: Int)

    private class Trailing(val values: List<NumTok>, val tokenCount: Int, val hadTimes: Boolean)

    /** Numbers at the end of the row (with "x", "@" and currency signs in between). */
    private fun trailingNumbers(tokens: List<String>): Trailing {
        val values = ArrayDeque<NumTok>()
        var count = 0
        var hadTimes = false
        for (t in tokens.asReversed()) {
            val token = t.trim('(', ')', ':')
            when {
                PRICE_TOKEN.matches(token) -> values.addFirst(numTok(token))
                QTY_X.matches(token) -> { values.addFirst(numTok(QTY_X.matchEntire(token)!!.groupValues[1])); hadTimes = true }
                TIMES.matches(token) -> hadTimes = true
                CURRENCY.matches(token) -> Unit
                else -> break
            }
            count++
        }
        return Trailing(values.toList(), count, hadTimes)
    }

    private fun numTok(token: String): NumTok {
        val plain = token.replace(",", "")
        val decimals = plain.substringAfter('.', "").length
        return NumTok(plain.toDouble(), decimals)
    }

    /** Picks quantity and line total from the numbers on a row. */
    private fun columns(values: List<NumTok>, hadTimes: Boolean): Pair<String, Double> {
        val amount = values.last().value
        return when (values.size) {
            1 -> "" to amount
            2 -> {
                val first = values[0]
                when {
                    // "2 x 160.00" with no line total printed
                    hadTimes && isCount(first) -> qtyText(first.value) to first.value * amount
                    // same number twice: price of one
                    first.value == amount -> "1" to amount
                    // small count + amount ("Eggs 12 300", weight "1.250 250.00")
                    isCount(first) && (first.value <= 50 || first.decimals == 3) -> qtyText(first.value) to amount
                    // rate + amount: qty = amount / rate when that is a whole number
                    else -> {
                        val q = if (first.value > 0) amount / first.value else 0.0
                        val whole = q >= 1 && kotlin.math.abs(q - Math.round(q)) < 0.02
                        (if (whole) qtyText(Math.round(q).toDouble()) else "") to amount
                    }
                }
            }
            else -> {
                // [… qty, rate, amount]
                val qty = values[values.size - 3]
                (if (isCount(qty)) qtyText(qty.value) else "") to amount
            }
        }
    }

    /** Counts are whole numbers ("2"); weights have 3 decimals ("1.250"); prices usually have 2. */
    private fun isCount(n: NumTok) = n.value > 0 && n.value <= 999 && (n.decimals == 0 || n.decimals == 3)

    private fun qtyText(v: Double) =
        if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')

    private fun isHeader(tokens: List<String>): Boolean {
        val words = tokens.map { it.lowercase(Locale.ROOT).trim(':', '.', '/') }.filter { it.isNotEmpty() }
        return words.size >= 2 && words.count { it in HEADER_WORDS } >= words.size - 1
    }

    private fun findDate(row: String): String {
        for ((regex, kind) in DATE_PATTERNS) {
            val m = regex.find(row) ?: continue
            val g = m.groupValues
            val date = try {
                when (kind) {
                    "ymd" -> LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt())
                    "dmy" -> LocalDate.of(g[3].toInt(), g[2].toInt(), g[1].toInt())
                    else -> LocalDate.of(2000 + g[3].toInt(), g[2].toInt(), g[1].toInt())
                }
            } catch (_: Exception) {
                null
            } ?: continue
            if (date.isAfter(LocalDate.now().plusDays(1)) || date.year < 2015) continue
            return date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
        return ""
    }

    /** "RICE BASMATI" -> "Rice Basmati" (bills are often all capitals). */
    private fun String.titleCaseIfShouting(): String {
        val letters = filter { it.isLetter() }
        if (letters.isEmpty() || letters.any { it.isLowerCase() } || letters.none { it in 'A'..'Z' }) return this
        return lowercase(Locale.ROOT).split(' ').joinToString(" ") { w ->
            w.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }
}
