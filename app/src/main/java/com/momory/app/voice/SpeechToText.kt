package com.momory.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Reconnaissance vocale via l'API SpeechRecognizer d'Android (reconnaissance
 * embarquée du téléphone — fonctionne même si le serveur Ollama n'a aucun
 * rapport avec la voix, tout le traitement audio→texte se fait côté Android).
 */
class SpeechToText(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (code: Int, message: String) -> Unit,
    private val onListeningChange: (Boolean) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError(-1, "Reconnaissance vocale indisponible sur cet appareil.")
            return
        }
        stop()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { onListeningChange(true) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { onListeningChange(false) }
                override fun onError(error: Int) {
                    onListeningChange(false)
                    this@SpeechToText.onError(error, errorText(error))
                }
                override fun onResults(results: Bundle?) {
                    onListeningChange(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) onResult(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer?.startListening(intent)
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }

    companion object {
        /** Erreurs "normales" (silence, rien compris) qu'on peut ignorer en mode écoute continue. */
        fun isRetryableError(code: Int): Boolean = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_CLIENT -> true
            else -> false
        }
    }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Rien compris — réessaie."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Aucune voix détectée."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission micro refusée."
        SpeechRecognizer.ERROR_NETWORK -> "Erreur réseau pendant la reconnaissance."
        else -> "Erreur de reconnaissance vocale (code $code)."
    }
}
