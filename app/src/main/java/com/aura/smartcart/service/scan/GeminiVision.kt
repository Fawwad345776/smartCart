package com.aura.smartcart.service.scan

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import org.json.JSONObject

/**
 * Reads bills and handwritten/printed lists with Gemini through Firebase AI Logic.
 * Works for every script (including Urdu and Arabic, which on-device ML Kit cannot read).
 * Requires "AI Logic" with the Gemini Developer API enabled in the Firebase console.
 */
class GeminiVision {

    private fun model(name: String): GenerativeModel =
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = name,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                temperature = 0.1f
            }
        )

    private suspend fun ask(bitmap: Bitmap, prompt: String): JSONObject? {
        for (name in MODELS) {
            try {
                val response = model(name).generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
                val text = response.text?.trim()?.removePrefix("```json")?.removePrefix("```")?.removeSuffix("```")
                if (!text.isNullOrBlank()) return JSONObject(text)
            } catch (e: Exception) {
                Log.w(TAG, "Gemini model $name failed", e)
            }
        }
        return null
    }

    /** Shopping-list lines, written in [languageName] as on the paper. */
    suspend fun readListLines(bitmap: Bitmap, languageName: String): List<String>? {
        val json = ask(
            bitmap,
            """
            This image is a shopping list. Read every item on it.
            The list is written in $languageName. Keep each item in the same language and script it is written in
            (for Roman Urdu keep English letters). Put the quantity with the item, e.g. "2 kg rice".
            Reply only with JSON: {"items": ["...", "..."]}
            """.trimIndent()
        ) ?: return null
        val arr = json.optJSONArray("items") ?: return null
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }

    /** Structured data from a shop receipt. */
    suspend fun readBill(bitmap: Bitmap, languageName: String): BillScan? {
        val json = ask(
            bitmap,
            """
            This image is a shop receipt / bill. Extract it.
            Item names: keep them as printed; if the bill is in $languageName keep that language.
            Prices are numbers only (no currency sign). Use the line total for each item.
            If something is missing use "" or 0.
            Reply only with JSON:
            {"store": "", "address": "", "date": "YYYY-MM-DD", "currency": "ISO code if printed",
             "items": [{"name": "", "quantity": "", "price": 0}], "total": 0}
            """.trimIndent()
        ) ?: return null

        val items = json.optJSONArray("items")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name").trim()
                if (name.isEmpty()) null else BillScanItem(name, o.optString("quantity").trim(), o.optDouble("price", 0.0))
            }
        }.orEmpty()

        return BillScan(
            store = json.optString("store").trim(),
            address = json.optString("address").trim(),
            date = json.optString("date").trim(),
            currency = json.optString("currency").trim().uppercase(),
            items = items,
            total = json.optDouble("total", 0.0).takeIf { !it.isNaN() } ?: 0.0
        )
    }

    private companion object {
        const val TAG = "GeminiVision"
        /** Tried in order; the second is a fallback if the first is not available on the project. */
        val MODELS = listOf("gemini-3.5-flash", "gemini-2.5-flash")
    }
}

data class BillScanItem(val name: String, val quantity: String, val price: Double)

data class BillScan(
    val store: String,
    val address: String,
    val date: String,
    val currency: String,
    val items: List<BillScanItem>,
    val total: Double
)
