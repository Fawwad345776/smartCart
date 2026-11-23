package com.aura.smartcart

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aura.smartcart.service.scan.BillScan
import com.aura.smartcart.service.scan.ScanOutcome
import com.aura.smartcart.service.scan.TesseractOcr
import com.aura.smartcart.service.scan.TextScanner
import com.aura.smartcart.util.InputLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Draws a printed Urdu / Arabic bill on the device and reads it back with the real scanner
 * (Tesseract offline OCR + ReceiptParser). Names are right-aligned and prices left-aligned, like real bills.
 */
@RunWith(AndroidJUnit4::class)
class UrduArabicScanTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scanner = TextScanner(context, TesseractOcr(context))

    @Test
    fun readsUrduBill() = runBlocking {
        val bill = readBill(
            InputLanguage.URDU,
            store = "الفلاح جنرل اسٹور",
            rows = listOf("چینی" to "320", "چاول" to "1450", "دودھ" to "360"),
            totalLabel = "کل" to "2130"
        )
        assertEquals(2130.0, bill.total, 0.01)
        assertTrue("expected at least 2 items, got ${bill.items}", bill.items.size >= 2)
    }

    @Test
    fun readsArabicBill() = runBlocking {
        val bill = readBill(
            InputLanguage.ARABIC,
            store = "سوبر ماركت النور",
            rows = listOf("سكر" to "320", "أرز" to "1450", "حليب" to "360"),
            totalLabel = "المجموع" to "2130"
        )
        assertEquals(2130.0, bill.total, 0.01)
        assertTrue("expected at least 2 items, got ${bill.items}", bill.items.size >= 2)
    }

    @Test
    fun readsUrduList() = runBlocking {
        val file = render(listOf("چینی" to "", "چاول" to "", "دودھ" to ""), store = null, total = null)
        val result = scanner.readList(Uri.fromFile(file), InputLanguage.URDU)
        Log.i(TAG, "Urdu list: $result")
        assertTrue("list not read: $result", result is ScanOutcome.Success && result.value.size >= 2)
    }

    private suspend fun readBill(
        language: InputLanguage,
        store: String,
        rows: List<Pair<String, String>>,
        totalLabel: Pair<String, String>
    ): BillScan {
        val file = render(rows, store, totalLabel)
        val result = scanner.readBill(Uri.fromFile(file), language)
        Log.i(TAG, "$language bill: $result")
        assertTrue("bill not read: $result", result is ScanOutcome.Success)
        return (result as ScanOutcome.Success).value
    }

    private fun render(rows: List<Pair<String, String>>, store: String?, total: Pair<String, String>?): File {
        val width = 1240
        val lineH = 110
        val lines = (if (store != null) 1 else 0) + rows.size + (if (total != null) 1 else 0)
        val bmp = Bitmap.createBitmap(width, 120 + lines * lineH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp).apply { drawColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 58f }
        var y = 120f
        fun row(name: String, price: String) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(name, width - 80f, y, paint)
            if (price.isNotEmpty()) {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(price, 80f, y, paint)
            }
            y += lineH
        }
        store?.let { row(it, "") }
        rows.forEach { (n, p) -> row(n, p) }
        total?.let { (n, p) -> row(n, p) }

        val file = File(context.cacheDir, "scan_test_${System.nanoTime()}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private companion object {
        const val TAG = "UrduArabicScanTest"
    }
}
