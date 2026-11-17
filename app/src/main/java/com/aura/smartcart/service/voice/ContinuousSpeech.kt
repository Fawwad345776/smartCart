package com.aura.smartcart.service.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Keeps listening until [stop] is called: every time the user pauses, the finished phrase is
 * delivered to [onPhrase] and listening starts again straight away, so items keep getting added
 * while they talk. Must be used from the main thread.
 *
 * Fast talkers: the recogniser sometimes ends a turn with no final result even though it showed
 * words while listening. The last partial text is kept and used in that case, so nothing is lost.
 */
class ContinuousSpeech(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onPhrase: (String) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onError: (Int) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var languageTag: String = "en-US"
    private val handler = Handler(Looper.getMainLooper())
    private var lastPartial: String = ""
    private var consecutiveClientErrors = 0

    var isRunning: Boolean = false
        private set

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(languageTag: String) {
        this.languageTag = languageTag
        isRunning = true
        lastPartial = ""
        if (recognizer == null) recognizer = createRecognizer()
        listenOnce()
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        flushPartial()
        recognizer?.stopListening()
    }

    fun destroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    /**
     * Prefers Google's recogniser when installed: it handles fast speech and Urdu/Hindi/Arabic
     * much better than some phone makers' default engines.
     */
    private fun createRecognizer(): SpeechRecognizer {
        val google = googleRecognizer()
        val r = if (google != null) {
            SpeechRecognizer.createSpeechRecognizer(context, google)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        r.setRecognitionListener(listener)
        return r
    }

    private fun googleRecognizer(): ComponentName? = try {
        context.packageManager
            .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .map { it.serviceInfo }
            .firstOrNull { it.packageName == GOOGLE_APP }
            ?.let { ComponentName(it.packageName, it.name) }
    } catch (_: Exception) {
        null
    }

    private fun listenOnce() {
        lastPartial = ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Give fast or long lists time: don't stop on a short breath between items.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 6000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            recreateAndRestart()
        }
    }

    private fun restartSoon(delayMs: Long = 60) {
        if (!isRunning) return
        handler.postDelayed({ if (isRunning) listenOnce() }, delayMs)
    }

    private fun recreateAndRestart() {
        recognizer?.destroy()
        recognizer = createRecognizer()
        restartSoon(300)
    }

    /** Uses what was heard so far when the recogniser gives up without a final result. */
    private fun flushPartial() {
        val text = lastPartial.trim()
        lastPartial = ""
        if (text.isNotEmpty()) onPhrase(text)
        onPartial("")
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            // After stop() the pending text was already delivered; ignore late callbacks.
            if (!isRunning) return
            when (error) {
                // Silence / not understood / engine hiccup: keep anything heard and listen again.
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    flushPartial()
                    restartSoon()
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restartSoon(400)
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                    flushPartial()
                    if (++consecutiveClientErrors <= 3) recreateAndRestart() else fail(error)
                }
                else -> {
                    flushPartial()
                    fail(error)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            if (!isRunning) return
            consecutiveClientErrors = 0
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                lastPartial = ""
                onPhrase(text)
                onPartial("")
            } else {
                flushPartial()
            }
            restartSoon()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!isRunning) return
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) lastPartial = text
            onPartial(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun fail(error: Int) {
        isRunning = false
        onError(error)
    }

    private companion object {
        const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    }
}
