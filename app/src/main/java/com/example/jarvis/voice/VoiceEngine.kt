// ============================================================
// voice/VoiceEngine.kt
// ------------------------------------------------------------
// STT via SpeechRecognizer nativo do Android (grátis, offline
// em muitos devices). TTS via TextToSpeech nativo.
// Para STT de qualidade superior (ex: Whisper), trocar por
// chamada à API — deixei o hook pronto em transcribeViaWhisper().
// ============================================================

package com.example.jarvis.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.jarvis.core.GiselleBus
import com.example.jarvis.core.GiselleState
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sin

class VoiceEngine(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val handler = Handler(Looper.getMainLooper())
    private val utterances = ConcurrentHashMap<String, String>()
    @Volatile private var lastVisemeAt = 0L
    private val lipSync = object : Runnable {
        override fun run() {
            if (GiselleBus.pendingSpeech.get() <= 0 || GiselleBus.state.value != GiselleState.SPEAKING) {
                GiselleBus.setMouthOpen(0f)
                return
            }
            if (System.currentTimeMillis() - lastVisemeAt > 300) {
                val phase = System.nanoTime() / 1_000_000_000.0
                GiselleBus.setMouthOpen((0.12 + abs(sin(phase * 7.2)) * 0.72).toFloat())
            }
            handler.postDelayed(this, 110)
        }
    }

    fun init() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("pt", "BR")
                escolherVoz()
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.05f)
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { utterances.remove(id); finishOne() }
                    override fun onError(id: String?) { utterances.remove(id); finishOne() }
                    override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                        val spoken = utterances[id] ?: return
                        val sample = spoken.substring(start.coerceIn(0, spoken.length), end.coerceIn(start.coerceIn(0, spoken.length), spoken.length))
                        val vowels = sample.lowercase().filter { it in "aáãeéêiíoóõuú" }
                        lastVisemeAt = System.currentTimeMillis()
                        GiselleBus.setMouthOpen(if (vowels.isEmpty()) 0.16f else vowels.map {
                            when (it) {
                                'a', 'á', 'ã' -> 0.95f
                                'e', 'é', 'ê', 'o', 'ó', 'õ' -> 0.62f
                                else -> 0.28f
                            }
                        }.average().toFloat())
                    }
                })
            }
        }
    }

    /** Aplica a melhor voz pt-BR instalada (ou a escolhida pelo utilizador em pref "voz_tts"). */
    private fun escolherVoz() {
        val motor = tts ?: return
        val instaladas = motor.voices.orEmpty()
        val candidatos = instaladas.map {
            VoiceCandidate(it.name, it.locale.language, it.locale.country, it.quality, it.isNetworkConnectionRequired)
        }
        val preferida = context.getSharedPreferences("giselle_ui", Context.MODE_PRIVATE).getString("voz_tts", null)
        val escolhida = VoiceSelection.escolher(candidatos, preferida) ?: return
        instaladas.firstOrNull { it.name == escolhida.name }?.let { motor.voice = it }
    }

    fun speak(text: String) {
        if (!ttsReady) return
        GiselleBus.pendingSpeech.incrementAndGet()
        GiselleBus.set(GiselleState.SPEAKING)
        GiselleBus.say(text)
        val id = "jarvis_utterance_${System.nanoTime()}"
        utterances[id] = text
        handler.removeCallbacks(lipSync)
        handler.post(lipSync)
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
    }

    /**
     * Escuta um único comando de voz e retorna via callback.
     * Uso: acionado por wake word externa ou botão físico/UI.
     */
    fun listenOnce(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Reconhecimento de voz não disponível neste device.")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    if (text != null) onResult(text) else onError("Não entendi.")
                }

                override fun onError(error: Int) {
                    onError(VoiceSelection.mensagemErroReconhecimento(error))
                }

                // Callbacks obrigatórios sem ação necessária aqui
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            }
            startListening(intent)
        }
    }

    /** Interrompe a escuta em curso (conversa por voz desligada). Não afeta o TTS. */
    fun cancelListening() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun finishOne() {
        if (GiselleBus.pendingSpeech.decrementAndGet() <= 0) {
            GiselleBus.pendingSpeech.set(0)
            handler.removeCallbacks(lipSync)
            GiselleBus.setMouthOpen(0f)
            if (!GiselleBus.taskActive) GiselleBus.set(GiselleState.IDLE)
        }
    }

    fun destroy() {
        handler.removeCallbacks(lipSync)
        utterances.clear()
        GiselleBus.setMouthOpen(0f)
        speechRecognizer?.destroy()
        tts?.shutdown()
    }

    // Hook para trocar por Whisper API se quiser precisão maior
    // suspend fun transcribeViaWhisper(audioFile: File): String { ... }
}
