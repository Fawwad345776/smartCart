package com.aura.smartcart.util

import java.util.Locale

/** A shopping item parsed from speech, typing or scanned text. */
data class ParsedItem(val name: String, val quantity: String)

/**
 * Turns free text into separate items with quantities, in English, Roman Urdu, Urdu, Hindi and Arabic.
 *
 *  "aadha pao dahi, 1 kilo cheeni aur do darjan anday" -> Dahi (½ pao), Cheeni (1 kilo), Anday (2 darjan)
 *  "dahi aadha pao cheeni ek kilo"                      -> Dahi (½ pao), Cheeni (1 kilo)
 *
 * Items are split on commas/new lines, on "and" words (and/aur/और/اور/و…), and — for people who
 * talk fast without pauses — wherever a new quantity starts after an item name.
 */
object ItemTextParser {

    internal val SEPARATORS = Regex(
        "[,،;\\n]+|\\s+(?:and|aur|or|phir|then|plus|also|और|फिर|तथा|اور|پھر|و|ثم|أيضا)\\s+",
        RegexOption.IGNORE_CASE
    )
    internal val BULLET = Regex("^\\s*(?:[-•*·▪●○□☐✓✔]|\\d+[.)]\\s|\\(\\d+\\))\\s*")
    private val DIGIT_THEN_LETTERS = Regex("(\\d)([^\\d\\s.,/½¼¾])")
    private val NUMBER = Regex("^\\d+(?:[.,]\\d+)?$|^\\d+/\\d+$|^[½¼¾]$|^\\d+[½¼¾]$")

    /** Spoken numbers and fractions -> what we show. */
    internal val NUMBER_WORDS: Map<String, String> = HashMap<String, String>().apply {
        fun add(value: String, vararg words: String) = words.forEach { word -> this[word] = value }
        add("1", "one", "ek", "aik", "ik", "ایک", "اک", "एक", "واحد", "واحدة")
        add("2", "two", "do", "دو", "दो", "اثنين", "اثنان", "إثنين")
        add("3", "three", "teen", "tin", "تین", "तीन", "ثلاثة", "ثلاث")
        add("4", "four", "char", "chaar", "چار", "चार", "أربعة", "اربعة", "أربع")
        add("5", "five", "panch", "paanch", "پانچ", "पांच", "पाँच", "خمسة", "خمس")
        add("6", "six", "chay", "chhe", "chhay", "چھ", "छह", "छः", "ستة")
        add("7", "seven", "saat", "سات", "सात", "سبعة")
        add("8", "eight", "aath", "آٹھ", "आठ", "ثمانية")
        add("9", "nine", "nau", "نو", "नौ", "تسعة")
        add("10", "ten", "das", "دس", "दस", "عشرة")
        add("12", "twelve", "barah", "baarah", "بارہ", "बारह")
        // Roman Urdu / Hindi tens and teens (speech engines sometimes write them as words)
        add("11", "gyarah", "gyara")
        add("13", "terah", "tera")
        add("14", "chaudah", "chauda", "chodah")
        add("15", "pandrah", "pandra")
        add("16", "solah", "sola")
        add("17", "satrah", "satra")
        add("18", "atharah", "athara")
        add("19", "unnees", "unees")
        add("20", "bees", "bis")
        add("25", "pachees", "pachis")
        add("30", "tees")
        add("40", "chalees", "chalis")
        add("50", "pachaas", "pachas", "pachaas")
        add("70", "sattar")
        add("80", "assi", "asi")
        add("90", "nabbe", "nabe")
        add("½", "half", "aadha", "adha", "aadhi", "adhi", "aadhe", "adhe", "آدھا", "آدھی", "آدھے", "आधा", "आधी", "आधे", "نصف", "نص")
        add("¼", "quarter", "ربع")
        add("¾", "paune", "pone", "پونے", "पौने")
        add("1¼", "sawa", "سوا", "सवा")
        add("1½", "dedh", "derh", "dairh", "dher", "ڈیڑھ", "ڈیڑہ", "डेढ़", "डेढ")
        add("2½", "dhai", "dhaai", "ڈھائی", "ढाई")
    }

    /** Measuring words. Kept as the user said them ("pao", "kilo", "darjan", …). */
    internal val UNITS: Set<String> = setOf(
        // English
        "kg", "kgs", "kilo", "kilos", "kilogram", "kilograms", "g", "gm", "gms", "gram", "grams", "gramme",
        "l", "lt", "ltr", "litre", "litres", "liter", "liters", "ml", "dozen", "dozens", "dz",
        "pc", "pcs", "piece", "pieces", "pack", "packs", "packet", "packets", "pkt",
        "bottle", "bottles", "box", "boxes", "can", "cans", "bag", "bags", "tin", "tins", "jar", "jars",
        "loaf", "loaves", "bunch", "bunches", "tray", "trays", "pound", "pounds", "lb", "lbs", "oz",
        // Roman Urdu / Hindi in English letters
        "pao", "pav", "pau", "paav", "paw", "paao", "pao", "darjan", "darjen", "dabba", "dabbe", "dibba", "dibbe",
        "botal", "bottal", "theli", "thaili", "gaddi", "gadi", "packit", "paket",
        // Urdu
        "پاؤ", "پاو", "پا", "کلو", "کلوگرام", "گرام", "لیٹر", "درجن", "پیکٹ", "ڈبہ", "ڈبے", "بوتل", "تھیلی", "گڈی", "عدد",
        // Hindi
        "पाव", "पाओ", "किलो", "किलोग्राम", "ग्राम", "लीटर", "दर्जन", "पैकेट", "डिब्बा", "डिब्बे", "बोतल", "थैली", "गड्डी",
        // Arabic
        "كيلو", "كيلوغرام", "كيلوجرام", "غرام", "جرام", "لتر", "دزينة", "درزن", "علبة", "علب", "كيس", "أكياس", "زجاجة", "ربطة", "حبة", "حبات"
    )

    /** Filler words people add while speaking ("mujhe … chahiye", "please get …"). */
    internal val FILLERS: Set<String> = setOf(
        "please", "buy", "get", "need", "want", "some", "i", "we", "also", "bhi",
        "mujhe", "hamein", "humein", "lena", "lene", "lana", "lao", "le", "aana", "chahiye", "chahye", "chaiye", "hai", "hain",
        "kharidna", "khareedna", "khareedni",
        "मुझे", "हमें", "लेना", "लाना", "चाहिए", "है", "हैं", "भी",
        "مجھے", "ہمیں", "لینا", "لانا", "چاہیے", "ہے", "ہیں", "بھی",
        "أريد", "اشتري", "نريد", "من", "فضلك"
    )

    /** Splits speech or typed text into items. */
    fun parse(text: String): List<ParsedItem> =
        normalize(text)
            .split(SEPARATORS)
            .flatMap { segment(it) }
            .distinctBy { it.name.lowercase(Locale.ROOT) to it.quantity }

    /** For scanned lists: each line is read on its own. */
    fun parseLines(lines: List<String>): List<ParsedItem> =
        lines.flatMap { line -> parse(line.replace(BULLET, "")) }

    /** Cleans a typed name when the quantity was typed in its own box. */
    fun cleanName(text: String): String =
        normalize(text).replace(BULLET, "").trim().trim('.', '-', ':').capitalizeFirst()

    internal fun normalize(text: String): String =
        Digits.toAscii(text)
            .replace(BULLET, "")
            .replace(DIGIT_THEN_LETTERS, "$1 $2") // "2kg" -> "2 kg"

    // ---------- splitting one phrase into items ----------

    private class Seg {
        val name = mutableListOf<String>()
        var qty: String? = null
        var qtyAfterName = false
    }

    private fun segment(phrase: String): List<ParsedItem> {
        val words = phrase.trim().split(Regex("\\s+"))
            .map { it.trim('.', ':', '!', '?', '"', '\'', '(', ')', '؟', '।') }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        val segs = mutableListOf<Seg>()
        var cur = Seg()
        var i = 0
        while (i < words.size) {
            val group = quantityAt(words, i)
            if (group != null) {
                val (qtyText, next) = group
                when {
                    // "aadha pao dahi": quantity first
                    cur.name.isEmpty() && cur.qty == null -> cur.qty = qtyText
                    // "dahi aadha pao": quantity after the name
                    cur.qty == null -> { cur.qty = qtyText; cur.qtyAfterName = true }
                    // a new quantity starts the next item ("… dahi ek kilo cheeni")
                    else -> { segs += cur; cur = Seg().apply { qty = qtyText } }
                }
                i = next
            } else {
                val w = words[i]
                if (cur.qty != null && cur.qtyAfterName) {
                    // "dahi aadha pao cheeni …": a name after a trailing quantity is the next item
                    segs += cur; cur = Seg()
                }
                if (w.lowercase(Locale.ROOT) !in FILLERS) cur.name += w
                i++
            }
        }
        segs += cur

        // A quantity left on its own belongs to the item before it.
        val out = mutableListOf<ParsedItem>()
        for (s in segs) {
            val name = s.name.joinToString(" ").trim()
            if (name.isEmpty() || name.none { it.isLetter() }) {
                val q = s.qty ?: continue
                val last = out.lastOrNull()
                if (last != null && last.quantity.isBlank()) out[out.lastIndex] = last.copy(quantity = q)
                continue
            }
            out += ParsedItem(name.capitalizeFirst(), s.qty.orEmpty())
        }
        return out
    }

    /**
     * Reads a quantity starting at [start]: numbers/fractions followed by an optional unit
     * ("1 pao", "aadha pao", "dedh kilo", "2 kg", "do darjan"), or a unit alone ("kilo cheeni").
     * Returns the text to show and the index after it.
     */
    private fun quantityAt(words: List<String>, start: Int): Pair<String, Int>? {
        val parts = mutableListOf<String>()
        var i = start
        while (i < words.size) {
            val number = numberOf(words[i], words.getOrNull(i + 1)) ?: break
            parts += number
            i++
        }
        val hasNumber = parts.isNotEmpty()
        if (i < words.size && isUnit(words[i])) {
            parts += words[i]
            i++
        }
        if (!hasNumber && parts.isEmpty()) return null
        // "1 aur aadha" style pieces are rare; join what we have ("1 ½" -> "1½").
        val text = parts.joinToString(" ").replace(Regex("(\\d) ([½¼¾])"), "$1$2")
        return text to i
    }

    internal fun numberOf(word: String, next: String?): String? {
        val w = word.lowercase(Locale.ROOT)
        if (NUMBER.matches(w)) return w.replace(',', '.')
        NUMBER_WORDS[w]?.let { return it }
        // "a dozen eggs", "an kilo …": only counts as 1 when a unit follows.
        if ((w == "a" || w == "an") && next != null && isUnit(next)) return "1"
        return null
    }

    internal fun isUnit(word: String): Boolean = word.lowercase(Locale.ROOT) in UNITS

    internal fun String.capitalizeFirst(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}
