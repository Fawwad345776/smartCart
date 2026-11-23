package com.aura.smartcart.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Joins the bill lines read from many video frames into one bill.
 *
 * While the user moves the camera down a long receipt, the same printed line is read in several
 * frames, sometimes with small OCR differences ("Rice 5kg" / "Rlce 5kg"). Lines with the same price
 * and a near-identical name are counted as one item. A line counts as confirmed once it was read in
 * [minHits] frames, which filters out one-off misreads.
 */
class VideoBillCollector(private val minHits: Int = 2) {

    private class Seen(var name: String, var quantity: String, val price: Double, var hits: Int) {
        val key: String = normalize(name)
    }

    private val seen = mutableListOf<Seen>()

    var store: String = ""
        private set
    var date: String = ""
        private set
    /** Last printed total seen (the total line is usually read near the end). */
    var total: Double = 0.0
        private set

    /** Adds the result of one frame. */
    fun add(frame: ReceiptResult) {
        if (store.isBlank()) store = frame.store
        if (date.isBlank()) date = frame.date
        if (frame.total > 0) total = frame.total

        // Two identical lines in the SAME frame are two real items (e.g. bought twice), so an
        // existing item can be matched only once per frame.
        val usedThisFrame = HashSet<Seen>()
        for (item in frame.items) {
            if (item.name.isBlank() || item.price <= 0) continue
            val key = normalize(item.name)
            val match = seen.firstOrNull { it !in usedThisFrame && samePrice(it.price, item.price) && similar(it.key, key) }
            if (match != null) {
                match.hits++
                if (match.quantity.isBlank()) match.quantity = item.quantity
                usedThisFrame += match
            } else {
                val added = Seen(item.name, item.quantity, item.price, 1)
                seen += added
                usedThisFrame += added
            }
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
        seen.clear(); store = ""; date = ""; total = 0.0
    }

    private companion object {
        fun normalize(name: String): String =
            name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

        fun samePrice(a: Double, b: Double) = abs(a - b) < 0.01

        /** Same name allowing a few OCR mistakes: about 1 wrong letter in 4. */
        fun similar(a: String, b: String): Boolean {
            if (a.isEmpty() || b.isEmpty()) return false
            if (a == b || a.contains(b) || b.contains(a)) return true
            val allowed = max(1, max(a.length, b.length) / 4)
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
