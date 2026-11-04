// business/InjectionDefense.kt — texto externo (vagas, feeds) é dado, nunca instrução.
// Honesto: é uma lista de padrões e pode ser contornada. A barreira real é a aprovação humana.
package com.example.jarvis.business

object InjectionDefense {
    private val SUSPICIOUS = listOf(
        Regex("ignore\\s+(all\\s+|any\\s+|the\\s+)?(previous|prior|above|earlier)\\s+(instructions?|prompts?|rules?)", RegexOption.IGNORE_CASE),
        Regex("(disregard|forget)\\s+(all\\s+|the\\s+)?(previous|prior|above|your)\\s+(instructions?|rules?)", RegexOption.IGNORE_CASE),
        Regex("aprove\\s+tudo|aprovar\\s+tudo|approve\\s+(all|everything)", RegexOption.IGNORE_CASE),
        Regex("system\\s+prompt|you\\s+are\\s+now|act\\s+as\\s+(an?\\s+)?(admin|system)", RegexOption.IGNORE_CASE),
        Regex("(send|transfer)\\s+(the\\s+)?(money|funds)|create\\s+(an?\\s+)?account", RegexOption.IGNORE_CASE),
        Regex("<\\s*script", RegexOption.IGNORE_CASE),
    )
    private val CONTROL = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")
    private val TAGS = Regex("<[^>]{0,500}>")
    private val SPACES = Regex("\\s+")

    /** Limpa controlo, HTML e espaços, e corta no limite. */
    fun clean(raw: String, maxChars: Int = 4000): String =
        raw.replace(TAGS, " ").replace(CONTROL, " ").replace(SPACES, " ").trim().take(maxChars)

    /** Devolve o texto limpo, ou null se houver padrão suspeito (a vaga é descartada). */
    fun screen(raw: String, maxChars: Int = 4000): String? {
        if (SUSPICIOUS.any { it.containsMatchIn(raw) }) return null
        val cleaned = clean(raw, maxChars)
        if (SUSPICIOUS.any { it.containsMatchIn(cleaned) }) return null
        return cleaned.ifBlank { null }
    }

    fun wrap(text: String): String = "<<<DADOS_EXTERNOS_NAO_CONFIAVEIS\n$text\n>>>"
}
