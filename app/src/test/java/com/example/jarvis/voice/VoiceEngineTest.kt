// ============================================================
// test/voice/VoiceEngineTest.kt
// ------------------------------------------------------------
// Via Robolectric. Cobre o caminho determinístico e mais
// importante para segurança: quando o reconhecimento de voz não
// está disponível no device, listenOnce() DEVE chamar onError,
// nunca onResult nem lançar exceção — evita o app travar
// silenciosamente esperando um callback que nunca chega.
//
// NÃO cobre o fluxo de sucesso completo (áudio real -> texto)
// nem TTS falando de verdade — isso depende de hardware/callback
// assíncrono do sistema que o Robolectric simula de forma frágil
// e não determinística; testar isso de verdade pede teste
// instrumentado em device/emulador real.
// ============================================================

package com.example.jarvis.voice

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.speech.SpeechRecognizer

@RunWith(RobolectricTestRunner::class)
class VoiceEngineTest {

    @Test
    fun `listenOnce chama onError quando reconhecimento de voz indisponivel`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        // Robolectric por padrão não registra um serviço de reconhecimento de
        // voz — isRecognitionAvailable() retorna false, cenário real e comum
        // em devices sem Google app ou com esse serviço desativado.
        val engine = VoiceEngine(context)

        var errorCalled = false
        var resultCalled = false

        engine.listenOnce(
            onResult = { resultCalled = true },
            onError = { errorCalled = true }
        )

        assertTrue(errorCalled)
        assertFalse(resultCalled)
    }

    @Test
    fun `speak antes de init nao lanca excecao, so nao faz nada`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val engine = VoiceEngine(context)
        // Não chama init() de propósito — ttsReady continua false.
        // O teste passa se speak() simplesmente não lançar exceção.
        engine.speak("texto qualquer")
    }

    @Test
    fun `destroy antes de init nao lanca excecao`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val engine = VoiceEngine(context)
        engine.destroy()
    }
}
