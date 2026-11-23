package com.aura.smartcart

import com.aura.smartcart.util.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rows as the phone really read them from video frames of a printed receipt (with OCR slips). */
class ReceiptOcrNoiseTest {

    @Test
    fun numberSlipsAreRepaired() {
        assertEquals("1 x 720.00   720.00", ReceiptParser.fixOcrNumbers("1 X 720:00   720.00"))
        assertEquals("Sub Total   9,025.00", ReceiptParser.fixOcrNumbers("Sub Total   9, 025.00"))
        assertEquals("Cash   9,000.00", ReceiptParser.fixOcrNumbers("Cash   9,000. 00"))
        assertEquals("2 x 165.00", ReceiptParser.fixOcrNumbers("2 x 165 .00"))
        assertEquals("1 x 620.00", ReceiptParser.fixOcrNumbers("1x 620,00"))
        assertEquals("2 x 165.00   330.00", ReceiptParser.fixOcrNumbers("2x165.00   330.00"))
        // Already correct numbers stay as they are.
        assertEquals("Rice 5kg   1,450.00", ReceiptParser.fixOcrNumbers("Rice 5kg   1,450.00"))
        assertEquals("Coca Cola 1.5L", ReceiptParser.fixOcrNumbers("Coca Cola 1.5L"))
        assertEquals("Coca Cola 1.5L", ReceiptParser.fixOcrNumbers("Coca Cola 1:5L"))
    }

    @Test
    fun noisyVideoFrameGivesCleanItems() {
        val rows = listOf(
            "market_receipt.pdfO",
            "1 * 465:00   465.00",
            "Surf Ecel 1kg",
            "1 X 720:00   720.00",
            "TSmato 1kg",
            "1 x 180:00   180.00",
            "Sugar 1kg",
            "2x165.00   330.00",
            "Basmati Rice 5kg",
            "1 x 2, 150.00   2, 150.00",
            "Coca Cola 1:5L",
            "2 x 190:00   380.00",
            // Laptop keyboard under the screen the receipt was shown on.
            "F12   PrtScr   Insert   Delete   8",
            "4 5 6 8 Backspace   8",
            "P   6",
            "Sub Total   9, 025.00",
            "NET TOTAL   Rs 8, 754.00",
            "Cash Received   9,000. 00"
        )
        val r = ReceiptParser.parse(rows)
        val byName = r.items.associate { it.name to it.price }
        assertEquals("items: ${r.items}", 720.0, byName["Surf Ecel 1kg"])
        assertEquals(180.0, byName["TSmato 1kg"])
        assertEquals(330.0, byName["Sugar 1kg"])
        assertEquals(2150.0, byName["Basmati Rice 5kg"])
        assertEquals(380.0, byName["Coca Cola 1.5L"])
        assertTrue("file name became an item: ${r.items}", r.items.none { "pdf" in it.name.lowercase() })
        assertEquals(5, r.items.size)
        assertEquals(8754.0, r.total, 0.001)
    }
}
