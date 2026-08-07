package com.momory.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TextToSpeechManager(
    context: Context,
    private val onSpeechFinished: (() -> Unit)? = null
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingVoiceName: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.FRENCH
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { onSpeechFinished?.invoke() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { onSpeechFinished?.invoke() }
                })
                ready = true
                pendingVoiceName?.let { applyVoice(it) }
            }
        }
    }

    /** Noms des voix françaises disponibles sur l'appareil (hors voix nécessitant le réseau). */
    fun availableVoiceNames(): List<String> {
        if (!ready) return emptyList()
        return tts?.voices
            ?.filter { it.locale.language.startsWith("fr") && !it.isNetworkConnectionRequired }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
    }

    fun applyVoice(voiceName: String) {
        if (voiceName.isBlank()) return
        if (!ready) {
            pendingVoiceName = voiceName
            return
        }
        val match = tts?.voices?.firstOrNull { it.name == voiceName } ?: return
        tts?.voice = match
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "momory_reply")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
