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
    /** Urdu/Arabic the phone could not read (usually handwriting): only Gemini (online) can read it. */
    data object NeedsInternet : ScanOutcome<Nothing>
}

/**
 * Reads scanned lists and bills in the chosen language.
 * Gemini is tried first for lists (best with handwriting) when it is available. Offline fallbacks:
 * ML Kit for English, Roman Urdu and Hindi; Tesseract for Urdu and Arabic. All of them are free.
 */
class TextScanner(
    private val context: Context,
    private val gemini: GeminiVision,
    private val tesseract: TesseractOcr
) {

    suspend fun readList(uri: Uri, language: InputLanguage): ScanOutcome<List<String>> {
        val bitmap = withContext(Dispatchers.IO) { ImageLoader.loadScaled(context, uri, maxDim = 2200) }
            ?: return ScanOutcome.Unreadable

        gemini.readListLines(bitmap, language.englishName())?.takeIf { it.isNotEmpty() }?.let {
            return ScanOutcome.Success(it)
        }

        if (language.script == Script.ARABIC) {
            val lines = tesseractRows(bitmap, language.tesseractCode())
            // Offline OCR reads printed Urdu/Arabic only; handwriting needs Gemini (online).
            return if (lines.isEmpty()) ScanOutcome.NeedsInternet else ScanOutcome.Success(lines)
        }
        val lines = onDeviceLines(bitmap, language.script)
        return if (lines.isEmpty()) ScanOutcome.Unreadable else ScanOutcome.Success(lines)
    }

    /**
     * Bills: on-device OCR first (fast, offline, free) — ML Kit, or Tesseract for Urdu/Arabic. Each
     * printed line is split into Item / Qty / Price. Gemini is used when OCR finds no priced items.
     */
    suspend fun readBill(uri: Uri, language: InputLanguage): ScanOutcome<BillScan> {
        val bitmap = withContext(Dispatchers.IO) { ImageLoader.loadScaled(context, uri, maxDim = 2200) }
            ?: return ScanOutcome.Unreadable

        run {
            val rows = if (language.script == Script.ARABIC) {
                tesseractRows(bitmap, language.tesseractCode())
            } else {
                onDeviceRows(bitmap, language.script)
            }
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
     * Urdu/Arabic rows, read with two free on-device engines together:
     * Tesseract reads the words, and ML Kit reads the 0-9 numbers (Tesseract's Urdu/Arabic models
     * misread them, e.g. 1450 -> 1040). Each line becomes "words  numbers", numbers right to left,
     * so the price printed on the left ends up last — where ReceiptParser looks for it.
     * When ML Kit finds no number on a line, Tesseract's own digits are kept (e.g. ١٢٣ digits).
     */
    private suspend fun tesseractRows(bitmap: Bitmap, code: String): List<String> {
        val lines = tesseract.readLines(bitmap, code)
        if (lines.isEmpty()) return emptyList()
        val numbers = latinNumbers(bitmap).toMutableList()

        val pieces = mutableListOf<Piece>()
        for (line in lines.sortedBy { it.box.top }) {
            val box = line.box
            val centerY = box.exactCenterY()
            val onLine = numbers.filter { n ->
                kotlin.math.abs(n.centerY - centerY) < 0.6 * box.height() && n.left < box.right + box.height()
            }
            val text = if (onLine.isEmpty()) {
                line.text
            } else {
                numbers.removeAll(onLine)
                val words = line.text.split(Regex("\\s+")).filterNot { LATIN_NUMBER.matches(it) }
                (words + onLine.sortedByDescending { it.left }.map { it.text }).joinToString("   ")
            }.trim()
            if (text.isNotEmpty()) pieces += Piece(text, box.left, box.top, box.bottom)
        }
        pieces += numbers // numbers Tesseract missed entirely, e.g. a price on its own line
        return groupIntoRows(pieces, rightToLeft = true)
    }

    /** 0-9 numbers on the page ("320", "1,450.00") with their positions, read by ML Kit's Latin model. */
    private suspend fun latinNumbers(bitmap: Bitmap): List<Piece> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            result.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { e ->
                val box = e.boundingBox ?: return@mapNotNull null
                val text = e.text.trim()
                if (LATIN_NUMBER.matches(text)) Piece(text, box.left, box.top, box.bottom) else null
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            recognizer.close()
        }
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

    private fun groupIntoRows(pieces: List<Piece>, rightToLeft: Boolean = false): List<String> {
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
            .map { r ->
                (if (rightToLeft) r.sortedByDescending { it.left } else r.sortedBy { it.left })
                    .joinToString("   ") { it.text }
            }
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

    private companion object {
        /** A plain 0-9 number token: "320", "1,450", "1450.00". */
        val LATIN_NUMBER = Regex("^\\d[\\d,]*(?:\\.\\d+)?$")
    }

    /** Tesseract language file for Arabic-script input. */
    private fun InputLanguage.tesseractCode(): String = if (this == InputLanguage.ARABIC) "ara" else "urd"

    private fun InputLanguage.englishName(): String = when (this) {
        InputLanguage.ENGLISH -> "English"
        InputLanguage.ROMAN_URDU -> "Roman Urdu (Urdu written in English letters)"
        InputLanguage.URDU -> "Urdu"
        InputLanguage.HINDI -> "Hindi"
        InputLanguage.ARABIC -> "Arabic"
    }
}
