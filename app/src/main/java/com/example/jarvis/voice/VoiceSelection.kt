package com.example.jarvis.voice

/**
 * Candidata de voz do TTS. Espelha os campos de android.speech.tts.Voice que usamos,
 * para que a escolha seja pura e testável na JVM.
 */
data class VoiceCandidate(
    val name: String,
    val language: String,
    val country: String,
    val quality: Int,
    val networkRequired: Boolean,
)

/**
 * Escolhe a voz pt-BR mais natural disponível.
 *
 * Android não expõe o gênero da voz. Por isso o gênero é uma heurística pelo nome,
 * e a preferência explícita do utilizador (pref "voz_tts") sempre vence.
 */
object VoiceSelection {

    fun escolher(candidatos: List<VoiceCandidate>, preferida: String? = null): VoiceCandidate? {
        val pt = candidatos.filter { it.language.equals("pt", ignoreCase = true) }
        if (pt.isEmpty()) return null
        if (!preferida.isNullOrBlank()) {
            pt.firstOrNull { it.name == preferida }?.let { return it }
        }
        return pt.maxByOrNull { pontuacao(it) }
    }

    internal fun pontuacao(v: VoiceCandidate): Int {
        val nome = v.name.lowercase()
        var pontos = 0
        if (v.country.equals("BR", ignoreCase = true)) pontos += 100
        pontos += v.quality * 10
        if (listOf("female", "feminina", "fem").any { nome.contains(it) }) pontos += 50
        if (listOf("male", "masculina", "masc").any { nome.contains(it) }) pontos -= 80
        if (v.networkRequired) pontos -= 5 // offline primeiro: a voz precisa funcionar sem rede
        return pontos
    }

    /** Mensagem amigável para os códigos de SpeechRecognizer. */
    fun mensagemErroReconhecimento(codigo: Int): String = when (codigo) {
        1, 2, 4 -> "Sem conexão para reconhecer a voz."           // NETWORK_TIMEOUT, NETWORK, SERVER
        6, 7 -> "Não ouvi nada útil. Tente de novo."             // SPEECH_TIMEOUT, NO_MATCH
        8 -> "O reconhecimento de voz está ocupado. Tente de novo."  // RECOGNIZER_BUSY
        9 -> "Permissão do microfone negada. Ative-a nas definições do app."
        else -> "Não consegui ouvir (código $codigo)."
    }
}
