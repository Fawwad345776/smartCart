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

    /** Frames as the phone really read a printed bill (OCR slips in names and one price). */
    @Test fun real_video_frames_give_each_item_once() {
        fun i(n: String, p: Double) = ReceiptItem(n, "", p)
        val c = VideoBillCollector()
        c.add(frame(i("Dalda Cooking 0il 1L", 620.0), i("Nestle Milkpak 1L", 990.0)))
        c.add(frame(i("Dalda Cooking 0il 1L", 620.0), i("Nestle Milkpak lL", 990.0), i("Sugar lkg", 330.0)))
        c.add(frame(i("Dalda Cooking..0i l", 620.0), i("Nestlc Milkpak LL", 990.0), i("Eggs (Dozen)", 380.0)))
        c.add(frame(i("Dalda Cosking ôil il", 620.0), i("Nestle Milkpak ii", 990.0), i("Bugar 1kg", 330.0), i("Eggs (Dozen)", 380.0)))
        c.add(frame(i("Nestle Milkpak 1i", 980.0), i("Sugar 1kg", 330.0), i("Eggs (Dozen)", 380.0), i("Coca Cola 1.5L", 380.0)))
        c.add(frame(i("Sugar 1kg", 330.0), i("Coca Cola 1.5L", 380.0)))

        val items = c.confirmed()
        assertEquals(items.joinToString { "${it.name}=${it.price}" }, 5, items.size)
        assertEquals(listOf("Dalda Cooking 0il 1L", "Nestle Milkpak 1L", "Sugar 1kg", "Eggs (Dozen)", "Coca Cola 1.5L"), items.map { it.name })
        assertEquals(listOf(620.0, 990.0, 330.0, 380.0, 380.0), items.map { it.price })
    }

    @Test fun keeps_last_total_and_falls_back_to_all_seen() {
        val c = VideoBillCollector()
        c.add(frame(rice, total = 1450.0))
        assertEquals(listOf(rice), c.result().items)
        assertEquals(1450.0, c.result().total, 0.001)
    }
}
