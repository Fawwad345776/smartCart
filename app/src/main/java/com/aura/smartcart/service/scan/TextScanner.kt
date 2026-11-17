package com.aura.smartcart.service.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.aura.smartcart.util.InputLanguage
import com.aura.smartcart.util.ReceiptParser
import com.aura.smartcart.util.Script
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface ScanOutcome<out T> {
    data class Success<T>(val value: T) : ScanOutcome<T>
    data object Unreadable : ScanOutcome<Nothing>
    /** Urdu/Arabic text needs Gemini (internet); on-device recognition cannot read it. */
    data object NeedsInternet : ScanOutcome<Nothing>
}

/**
 * Reads scanned lists and bills in the chosen language.
 * Gemini is tried first (best quality, all scripts); on-device ML Kit is the offline fallback
 * for English, Roman Urdu and Hindi.
 */
class TextScanner(private val context: Context, private val gemini: GeminiVision) {

    suspend fun readList(uri: Uri, language: InputLanguage): ScanOutcome<List<String>> {
        val bitmap = withContext(Dispatchers.IO) { ImageLoader.loadScaled(context, uri) }
            ?: return ScanOutcome.Unreadable

        gemini.readListLines(bitmap, language.englishName())?.takeIf { it.isNotEmpty() }?.let {
            return ScanOutcome.Success(it)
        }
        if (language.script == Script.ARABIC) return ScanOutcome.NeedsInternet

        val lines = onDeviceLines(bitmap, language.script)
        return if (lines.isEmpty()) ScanOutcome.Unreadable else ScanOutcome.Success(lines)
    }

    /**
     * Bills: on-device OCR first (fast, offline, free) for English/Roman Urdu/Hindi — each printed
     * line is split into Item / Qty / Price. Gemini is used when OCR finds no priced items, and
     * for Urdu/Arabic bills, which OCR cannot read.
     */
    suspend fun readBill(uri: Uri, language: InputLanguage): ScanOutcome<BillScan> {
        val bitmap = withContext(Dispatchers.IO) { ImageLoader.loadScaled(context, uri, maxDim = 2200) }
            ?: return ScanOutcome.Unreadable

        if (language.script != Script.ARABIC) {
            val rows = onDeviceRows(bitmap, language.script)
            val receipt = ReceiptParser.parse(rows)
            if (receipt.items.isNotEmpty()) {
                return ScanOutcome.Success(
                    BillScan(
                        store = receipt.store,
                        address = "",
                        date = receipt.date,
                        currency = "",
                        items = receipt.items.map { BillScanItem(it.name, it.quantity, it.price) },
                        total = receipt.total
                    )
                )
            }
        }

        gemini.readBill(bitmap, language.englishName())?.takeIf { it.items.isNotEmpty() || it.total > 0 }?.let {
            return ScanOutcome.Success(it)
        }
        return if (language.script == Script.ARABIC) ScanOutcome.NeedsInternet else ScanOutcome.Unreadable
    }

    /**
     * Reads the page as printed rows: OCR pieces whose vertical centres line up are joined left to
     * right, so "Rice 5kg" and "1,450.00" on the same printed line become one row.
     */
    private suspend fun onDeviceRows(bitmap: Bitmap, script: Script): List<String> {
        val recognizer = when (script) {
            Script.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
        return try {
            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            val pieces = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                Piece(line.text.trim(), box.left, box.top, box.bottom)
            }.filter { it.text.isNotEmpty() }
            groupIntoRows(pieces)
        } catch (_: Exception) {
            emptyList()
        } finally {
            recognizer.close()
        }
    }

    private class Piece(val text: String, val left: Int, val top: Int, val bottom: Int) {
        val centerY get() = (top + bottom) / 2f
        val height get() = (bottom - top).coerceAtLeast(1)
    }

    private fun groupIntoRows(pieces: List<Piece>): List<String> {
        val rows = mutableListOf<MutableList<Piece>>()
        for (p in pieces.sortedBy { it.centerY }) {
            val row = rows.lastOrNull { r ->
                val cy = r.map { it.centerY }.average()
                val h = r.map { it.height }.average()
                kotlin.math.abs(p.centerY - cy) < 0.55 * minOf(h, p.height.toDouble())
            }
            if (row != null) row += p else rows += mutableListOf(p)
        }
        return rows
            .sortedBy { r -> r.minOf { it.top } }
            .map { r -> r.sortedBy { it.left }.joinToString("   ") { it.text } }
    }

    private suspend fun onDeviceLines(bitmap: Bitmap, script: Script): List<String> {
        val recognizer = when (script) {
            Script.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
        return try {
            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            result.textBlocks.flatMap { block -> block.lines.map { it.text.trim() } }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        } finally {
            recognizer.close()
        }
    }

    private fun InputLanguage.englishName(): String = when (this) {
        InputLanguage.ENGLISH -> "English"
        InputLanguage.ROMAN_URDU -> "Roman Urdu (Urdu written in English letters)"
        InputLanguage.URDU -> "Urdu"
        InputLanguage.HINDI -> "Hindi"
        InputLanguage.ARABIC -> "Arabic"
    }
}
