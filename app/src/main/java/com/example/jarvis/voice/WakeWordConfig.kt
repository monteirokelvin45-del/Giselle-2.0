// ============================================================
// voice/WakeWordConfig.kt
// ------------------------------------------------------------
// Lógica pura do WakeWordService (sem Android), extraída para ser
// testável. O reconhecimento em si usa Vosk offline (sem chave de
// terceiros); o microfone real fica fora da cobertura de teste.
// ============================================================

package com.example.jarvis.voice

object WakeWordConfig {
    fun isConfigured(accessKey: String): Boolean = accessKey.isNotBlank()
}
