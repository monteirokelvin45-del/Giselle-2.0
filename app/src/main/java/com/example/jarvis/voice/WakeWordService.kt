// ============================================================
// voice/WakeWordService.kt — Fase 3 (Giselle)
// Wake word "Giselle" + STT 100% offline com Vosk (sem Google, sem chave).
// Fluxo: IDLE (escuta só a palavra) → LISTENING (frase livre) → THINKING (agente)
//        → volta a escutar a palavra quando o estado regressa a IDLE.
// ============================================================
package com.example.jarvis.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.jarvis.core.FeedItem
import com.example.jarvis.core.FeedItemType
import com.example.jarvis.core.GiselleBus
import com.example.jarvis.core.GiselleState
import com.example.jarvis.core.JarvisForegroundService
import com.example.jarvis.core.JarvisRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class WakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWordService"
        private const val CHANNEL = "giselle_voice"
        private const val NOTIF_ID = 1002
        // Palavras fora do vocabulário do modelo são ignoradas; variantes aumentam a taxa de acerto.
        private const val WAKE_GRAMMAR = "[\"giselle\", \"gisele\", \"jizele\", \"[unk]\"]"
        private val WAKE_REGEX = Regex("gis+ele?|jizele", RegexOption.IGNORE_CASE)

        /** Mantido por compatibilidade com MainActivity (não é mais usado pelo wake word). */
        var onWakeWordDetected: (() -> Unit)? = null

        @Volatile private var instance: WakeWordService? = null
        fun triggerListen() { instance?.startCommand() }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var model: Model? = null
    private var speech: SpeechService? = null
    private val busy = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        try {
            startAsForeground()
        } catch (e: SecurityException) {
            instance = null
            stopSelf(startId)
            return START_NOT_STICKY
        } catch (e: IllegalStateException) {
            instance = null
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (model == null) scope.launch { if (loadModel()) startWake() } else if (speech == null) scope.launch { startWake() }
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Giselle (voz)", NotificationManager.IMPORTANCE_LOW))
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Giselle à escuta")
            .setContentText("Diz \"Giselle\" para falar comigo")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIF_ID, n)
    }

    private fun feed(text: String) =
        JarvisRepository.pushFeedItem(FeedItem(text = text, type = FeedItemType.INFO))

    private fun loadModel(): Boolean {
        val dir = File(filesDir, "vosk-model")
        if (!dir.exists()) {
            feed("A descarregar o modelo de voz offline (~30 MB, só uma vez)…")
            if (!ModelDownloader.download(filesDir, dir)) {
                feed("Falha ao descarregar o modelo de voz. Verifica a internet e reabre o app.")
                stopSelf(); return false
            }
        }
        return try { model = Model(dir.absolutePath); feed("Voz offline pronta. Diz \"Giselle\"."); true }
        catch (e: Exception) { Log.e(TAG, "modelo inválido: ${e.message}"); dir.deleteRecursively(); false }
    }

    @Synchronized private fun stopSpeech() {
        runCatching { speech?.stop(); speech?.shutdown() }
        speech = null
    }

    // ---------- Modo 1: só a palavra de ativação ----------
    @Synchronized private fun startWake() {
        val m = model ?: return
        stopSpeech(); busy.set(false)
        runCatching {
            val rec = Recognizer(m, 16000f, WAKE_GRAMMAR)
            speech = SpeechService(rec, 16000f).also { s ->
                s.startListening(object : RecognitionListener {
                    private fun check(h: String?) { if (h != null && WAKE_REGEX.containsMatchIn(h)) startCommand() }
                    override fun onPartialResult(h: String?) = check(h)
                    override fun onResult(h: String?) = check(h)
                    override fun onFinalResult(h: String?) {}
                    override fun onError(e: Exception?) { scope.launch { delay(2000); startWake() } }
                    override fun onTimeout() {}
                })
            }
        }.onFailure { Log.e(TAG, "wake falhou: ${it.message}") }
    }

    // ---------- Modo 2: frase do comando ----------
    fun startCommand() {
        if (!busy.compareAndSet(false, true)) return
        scope.launch {
            val m = model ?: run { busy.set(false); return@launch }
            stopSpeech()
            GiselleBus.set(GiselleState.LISTENING)
            runCatching {
                val rec = Recognizer(m, 16000f)
                speech = SpeechService(rec, 16000f).also { s ->
                    s.startListening(object : RecognitionListener {
                        override fun onPartialResult(h: String?) { GiselleBus.pulse() }
                        override fun onResult(h: String?) {
                            val t = clean(runCatching { JSONObject(h ?: "{}").optString("text") }.getOrDefault(""))
                            if (t.isNotBlank()) finish(t)
                        }
                        override fun onFinalResult(h: String?) {}
                        override fun onError(e: Exception?) { giveUp() }
                        override fun onTimeout() { giveUp() }
                    }, 8000)
                }
            }.onFailure { giveUp() }
        }
    }

    private fun clean(t: String) = t.replace(Regex("^\\s*(gis+ele?|jizele)[ ,]*", RegexOption.IGNORE_CASE), "").trim()

    private fun finish(text: String) {
        scope.launch {
            stopSpeech()
            GiselleBus.set(GiselleState.THINKING)
            ContextCompat.startForegroundService(
                this@WakeWordService,
                Intent(this@WakeWordService, JarvisForegroundService::class.java).apply {
                    action = JarvisForegroundService.ACTION_USER_COMMAND
                    putExtra(JarvisForegroundService.EXTRA_COMMAND, text)
                }
            )
            // Não escuta enquanto pensa/fala (evita ouvir a própria voz); volta quando IDLE.
            withTimeoutOrNull(60_000) { GiselleBus.state.first { it == GiselleState.IDLE } }
            delay(500)
            startWake()
        }
    }

    private fun giveUp() {
        scope.launch { GiselleBus.set(GiselleState.IDLE); delay(300); startWake() }
    }

    override fun onDestroy() {
        stopSpeech(); runCatching { model?.close() }; model = null; instance = null
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
