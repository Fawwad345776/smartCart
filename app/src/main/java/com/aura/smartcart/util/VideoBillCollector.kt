package com.aura.smartcart.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Joins the bill lines read from many video frames into one bill.
 *
 * While the user moves the camera down a long receipt, the same printed line is read in several
 * frames, often with OCR slips ("Nestle Milkpak 1L" / "Nestlc Milkpak lL", 990 / 980). Those are
 * counted as one item:
 *  - names are compared after mapping look-alike characters (0/o, 1/l/i, 5/s) and allow about
 *    one wrong letter in three;
 *  - the same price, or a price one digit off when the names are nearly identical.
 * Two lines read together in the SAME frame are always two items (e.g. bought twice, or two
 * different products), so they are never merged.
 * The name and price shown are the ones read most often. A line counts as confirmed once it was
 * read in [minHits] frames, which filters out one-off misreads.
 */
class VideoBillCollector(private val minHits: Int = 2) {

    private class Seen(name: String, var quantity: String, price: Double, frame: Int) {
        val names = LinkedHashMap<String, Int>().apply { put(name, 1) }
        val prices = LinkedHashMap<Double, Int>().apply { put(price, 1) }
        val keys = mutableSetOf(normalize(name))
        val frames = mutableSetOf(frame)
        var hits = 1

        val name: String get() = names.maxByOrNull { it.value }!!.key // first one wins a tie
        val price: Double get() = prices.maxByOrNull { it.value }!!.key

        fun add(name: String, quantity: String, price: Double, frame: Int) {
            hits++
            names.merge(name, 1, Int::plus)
            prices.merge(price, 1, Int::plus)
            keys += normalize(name)
            frames += frame
            if (this.quantity.isBlank()) this.quantity = quantity
        }

        fun absorb(other: Seen) {
            hits += other.hits
            other.names.forEach { (n, c) -> names.merge(n, c, Int::plus) }
            other.prices.forEach { (p, c) -> prices.merge(p, c, Int::plus) }
            keys += other.keys
            frames += other.frames
            if (quantity.isBlank()) quantity = other.quantity
        }
    }

    private val seen = mutableListOf<Seen>()
    private var frameNo = 0

    var store: String = ""
        private set
    var date: String = ""
        private set
    /** Last printed total seen (the total line is usually read near the end). */
    var total: Double = 0.0
        private set

    /** Adds the result of one frame. */
    fun add(frame: ReceiptResult) {
        frameNo++
        if (store.isBlank()) store = frame.store
        if (date.isBlank()) date = frame.date
        if (frame.total > 0) total = frame.total

        // Two identical lines in the SAME frame are two real items, so an existing item can be
        // matched only once per frame.
        val usedThisFrame = HashSet<Seen>()
        for (item in frame.items) {
            if (item.name.isBlank() || item.price <= 0) continue
            val key = normalize(item.name)
            val free = seen.filter { it !in usedThisFrame }
            val match = free.firstOrNull { s -> s.prices.keys.any { samePrice(it, item.price) } && s.keys.any { similar(it, key) } }
                ?: free.firstOrNull { s -> s.prices.keys.any { oneDigitOff(it, item.price) } && s.keys.any { nearlySame(it, key) } }
            if (match != null) {
                match.add(item.name, item.quantity, item.price, frameNo)
                usedThisFrame += match
            } else {
                val added = Seen(item.name, item.quantity, item.price, frameNo)
                seen += added
                usedThisFrame += added
            }
        }
        mergeVariants()
    }

    /**
     * Two entries can turn out to be the same line once more spellings are known
     * (A ~ C and B ~ C, but A was not close to B). Merge them unless they were read in the same frame.
     */
    private fun mergeVariants() {
        var i = 0
        while (i < seen.size) {
            var j = i + 1
            while (j < seen.size) {
                val a = seen[i]
                val b = seen[j]
                val together = a.frames.any { it in b.frames }
                val same = !together &&
                    (samePrice(a.price, b.price) && a.keys.any { ka -> b.keys.any { similar(ka, it) } } ||
                        oneDigitOff(a.price, b.price) && a.keys.any { ka -> b.keys.any { nearlySame(ka, it) } })
                if (same) {
                    a.absorb(b)
                    seen.removeAt(j)
                } else {
                    j++
                }
            }
            i++
        }
    }

    /** Items read in at least [minHits] frames, in the order they were first seen (top to bottom). */
    fun confirmed(): List<ReceiptItem> = seen.filter { it.hits >= minHits }.map { ReceiptItem(it.name, it.quantity, it.price) }

    /** What to keep when the user taps Done: confirmed items, or everything seen if none was confirmed. */
    fun result(): ReceiptResult {
        val items = confirmed().ifEmpty { seen.map { ReceiptItem(it.name, it.quantity, it.price) } }
        return ReceiptResult(store = store, date = date, items = items, total = total)
    }

    fun clear() {
        seen.clear(); frameNo = 0; store = ""; date = ""; total = 0.0
    }

    private companion object {
        /** Lower case, letters and digits only, look-alike characters made the same. */
        fun normalize(name: String): String =
            name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.map {
                when (it) {
                    '0' -> 'o'
                    '1', 'i', '|', '!' -> 'l'
                    '5' -> 's'
                    '8' -> 'b'
                    'ô', 'ö', 'ó' -> 'o'
                    else -> it
                }
            }.joinToString("")

        fun samePrice(a: Double, b: Double) = abs(a - b) < 0.01

        /** 990 vs 980, 1450 vs 1430: same number of digits, exactly one different. */
        fun oneDigitOff(a: Double, b: Double): Boolean {
            val x = Math.round(a * 100).toString()
            val y = Math.round(b * 100).toString()
            return x.length == y.length && x != y && x.indices.count { x[it] != y[it] } == 1
        }

        /** Same name allowing OCR mistakes: about 1 wrong letter in 3. */
        fun similar(a: String, b: String): Boolean = close(a, b, 3)

        /** Used when the price differs too: about 1 wrong letter in 6. */
        fun nearlySame(a: String, b: String): Boolean = close(a, b, 6)

        fun close(a: String, b: String, per: Int): Boolean {
            if (a.isEmpty() || b.isEmpty()) return false
            if (a == b) return true
            val shorter = min(a.length, b.length)
            // "milkpak" inside "nestlemilkpakll": only for reasonably long names.
            if (shorter >= 6 && (a.contains(b) || b.contains(a))) return true
            val allowed = max(1, max(a.length, b.length) / per)
            if (abs(a.length - b.length) > allowed) return false
            return distance(a, b) <= allowed
        }

        /** Levenshtein edit distance. */
        fun distance(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                }
                val t = prev; prev = cur; cur = t
            }
            return prev[b.length]
        }
    }
}
