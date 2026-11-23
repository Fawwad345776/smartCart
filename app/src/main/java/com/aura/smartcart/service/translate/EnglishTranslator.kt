package com.aura.smartcart.service.translate

import android.util.Log
import com.aura.smartcart.util.InputLanguage
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Turns bill text (store, items, address) into English before it is saved, so Firebase always
 * holds English. Free: Google ML Kit on-device translation (one ~30 MB model per language,
 * downloaded once, then works offline) for Urdu, Hindi and Arabic script; a built-in grocery
 * dictionary for Roman Urdu ("chawal" -> "rice"). English text is left as it is.
 * If a model is not downloaded yet and there is no internet, the original text is kept.
 */
class EnglishTranslator {

    private val translators = HashMap<String, Translator>()
    private val lock = Mutex()

    /** [hint]: the language the user picked for typing/voice; tells Urdu and Arabic apart when unsure. */
    suspend fun toEnglish(text: String, hint: InputLanguage): String {
        val t = text.trim()
        if (t.isEmpty()) return text
        val source = sourceOf(t, hint) ?: return RomanUrdu.toEnglish(t)
        return try {
            val translator = translator(source)
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            translator.translate(t).await().trim().ifEmpty { t }
        } catch (e: Exception) {
            Log.w(TAG, "Translation to English failed, keeping original", e)
            t
        }
    }

    /** Downloads the model for [language] in the background so the first save is quick. */
    suspend fun prefetch(language: InputLanguage) {
        val source = when (language) {
            InputLanguage.URDU -> TranslateLanguage.URDU
            InputLanguage.HINDI -> TranslateLanguage.HINDI
            InputLanguage.ARABIC -> TranslateLanguage.ARABIC
            else -> return
        }
        try {
            translator(source).downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        } catch (e: Exception) {
            Log.w(TAG, "Model download failed", e)
        }
    }

    private suspend fun translator(source: String): Translator = lock.withLock {
        translators.getOrPut(source) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                    .build()
            )
        }
    }

    private companion object {
        const val TAG = "EnglishTranslator"
        /** Letters used in Urdu but not in Arabic. */
        const val URDU_ONLY = "ٹڈڑںےۓھہکگیپچژ"
        /** Letters used in Arabic but not in Urdu. */
        const val ARABIC_ONLY = "ةكيى"

        /** ML Kit source language, or null for Latin script (English / Roman Urdu). */
        fun sourceOf(text: String, hint: InputLanguage): String? {
            if (text.any { it in 'ऀ'..'ॿ' }) return TranslateLanguage.HINDI
            val arabicScript = text.any { it in '؀'..'ۿ' || it in 'ݐ'..'ݿ' || it in 'ﭐ'..'﷿' || it in 'ﹰ'..'﻿' }
            if (!arabicScript) return null
            return when {
                text.any { it in URDU_ONLY } -> TranslateLanguage.URDU
                text.any { it in ARABIC_ONLY } -> TranslateLanguage.ARABIC
                hint == InputLanguage.ARABIC -> TranslateLanguage.ARABIC
                else -> TranslateLanguage.URDU
            }
        }
    }
}
