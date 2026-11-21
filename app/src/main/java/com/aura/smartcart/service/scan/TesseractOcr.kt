package com.aura.smartcart.service.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** One printed line read by Tesseract, with where it sits on the page. */
class OcrLine(val text: String, val box: Rect)

/**
 * Offline OCR for Urdu and Arabic, which ML Kit cannot read. Free and open source (Tesseract, Apache 2.0):
 * no internet, no API key, no Firebase setup. The language files ship in assets/tessdata and are
 * copied once to app storage, because Tesseract reads them from a file path.
 */
class TesseractOcr(private val context: Context) {

    private val mutex = Mutex()

    /** [language] is "urd" or "ara". Returns lines top to bottom, or empty if nothing could be read. */
    suspend fun readLines(bitmap: Bitmap, language: String): List<OcrLine> = mutex.withLock {
        withContext(Dispatchers.Default) {
            val dataDir = ensureLanguage(language) ?: return@withContext emptyList()
            val tess = TessBaseAPI()
            val gray = toGrayscale(bitmap)
            try {
                if (!tess.init(dataDir.absolutePath, language)) return@withContext emptyList()
                // AUTO read test bills best; SINGLE_BLOCK dropped lines and added noise on Urdu bills.
                tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                tess.setImage(gray)
                tess.getUTF8Text() // runs recognition; lines are read from the iterator below

                val lines = mutableListOf<OcrLine>()
                val it = tess.resultIterator ?: return@withContext emptyList()
                try {
                    it.begin()
                    do {
                        val text = it.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)?.trim().orEmpty()
                        val box = it.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                        val confidence = it.confidence(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                        // Handwriting and noise come back as low-confidence gibberish: drop it
                        // so the user gets "couldn't read" instead of wrong items.
                        val readable = confidence >= MIN_CONFIDENCE && text.count { c -> c.isLetterOrDigit() } >= 2
                        Log.d(TAG, "[$language] conf=${confidence.toInt()} keep=$readable box=$box text=$text")
                        // Boxes are moved back by the white border so they match the original image.
                        if (readable && box != null) lines += OcrLine(text, Rect(box).apply { offset(-BORDER, -BORDER) })
                    } while (it.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
                } finally {
                    it.delete()
                }
                lines
            } catch (e: Exception) {
                Log.w(TAG, "Tesseract failed", e)
                emptyList()
            } finally {
                tess.recycle()
                gray.recycle()
            }
        }
    }

    /** Copies assets/tessdata/<lang>.traineddata to files/tesseract/tessdata once. Returns the parent folder. */
    private fun ensureLanguage(language: String): File? = try {
        // Versioned folder: bumping MODELS_VERSION replaces files copied by an older app version.
        File(context.filesDir, "tesseract").takeIf { it.exists() }?.deleteRecursively()
        val root = File(context.filesDir, "tesseract-v$MODELS_VERSION")
        val dir = File(root, "tessdata").apply { mkdirs() }
        val file = File(dir, "$language.traineddata")
        if (!file.exists() || file.length() == 0L) {
            val tmp = File(dir, "$language.traineddata.tmp")
            context.assets.open("tessdata/$language.traineddata").use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (!tmp.renameTo(file)) error("Could not move $tmp")
        }
        root
    } catch (e: Exception) {
        Log.w(TAG, "Could not prepare $language language file", e)
        null
    }

    /**
     * Grey, slightly higher-contrast copy with a white border: Tesseract reads printed text better
     * without colour, and misses lines that touch the edge of the image.
     */
    private fun toGrayscale(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width + 2 * BORDER, src.height + 2 * BORDER, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out).apply { drawColor(android.graphics.Color.WHITE) }
        val contrast = 1.3f
        val shift = (-0.5f * contrast + 0.5f) * 255f
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, shift,
                    0f, contrast, 0f, 0f, shift,
                    0f, 0f, contrast, 0f, shift,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        canvas.drawBitmap(src, BORDER.toFloat(), BORDER.toFloat(), Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return out
    }

    private companion object {
        const val TAG = "TesseractOcr"
        /** 0-100. Clean printed lines usually score 75+; handwriting mostly scores below 50. */
        const val MIN_CONFIDENCE = 60f
        /** Bump when a file in assets/tessdata changes. v2: Arabic switched to the "fast" model (the "best" one fails to load). */
        const val MODELS_VERSION = 2
        /** White margin added around the image, in px. */
        const val BORDER = 40
    }
}
