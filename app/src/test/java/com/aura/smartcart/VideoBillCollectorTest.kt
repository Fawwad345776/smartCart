package com.aura.smartcart

import com.aura.smartcart.util.ReceiptItem
import com.aura.smartcart.util.ReceiptResult
import com.aura.smartcart.util.VideoBillCollector
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoBillCollectorTest {
    private fun frame(vararg items: ReceiptItem, total: Double = 0.0) =
        ReceiptResult(store = "", date = "", items = items.toList(), total = total)

    private val rice = ReceiptItem("Rice Basmati 5kg", "5 kg", 1450.0)
    private val milk = ReceiptItem("Milk Pack 1L", "2", 360.0)
    private val sugar = ReceiptItem("Sugar", "2", 320.0)

    @Test fun same_line_in_many_frames_is_one_item() {
        val c = VideoBillCollector()
        c.add(frame(rice, milk))
        c.add(frame(rice, milk))
        c.add(frame(milk, sugar))
        c.add(frame(sugar))
        assertEquals(listOf(rice, milk, sugar), c.confirmed())
    }

    @Test fun small_ocr_mistakes_are_merged() {
        val c = VideoBillCollector()
        c.add(frame(rice))
        c.add(frame(ReceiptItem("Rlce Basmati 5kg", "5 kg", 1450.0)))
        assertEquals(listOf(rice), c.confirmed())
    }

    @Test fun one_off_misread_is_dropped() {
        val c = VideoBillCollector()
        c.add(frame(rice, milk))
        c.add(frame(rice, milk, ReceiptItem("xx#q", "", 99.0)))
        assertEquals(listOf(rice, milk), c.confirmed())
    }

    @Test fun same_item_twice_on_bill_stays_twice() {
        val c = VideoBillCollector()
        c.add(frame(milk, milk))
        c.add(frame(milk, milk))
        assertEquals(listOf(milk, milk), c.confirmed())
    }

    @Test fun keeps_last_total_and_falls_back_to_all_seen() {
        val c = VideoBillCollector()
        c.add(frame(rice, total = 1450.0))
        assertEquals(listOf(rice), c.result().items)
        assertEquals(1450.0, c.result().total, 0.001)
    }
}
