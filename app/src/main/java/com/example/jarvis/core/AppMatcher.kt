// ============================================================
// core/AppMatcher.kt
// ------------------------------------------------------------
// Lógica pura de resolução "nome falado -> app instalado".
// Retorna um MatchResult tipado em vez de um app cru: nomes
// genéricos ("mensagens", "câmera") podem casar com VÁRIOS apps
// por match parcial — antes, o primeiro da lista vencia em
// silêncio, o que é justamente o risco que motivou este item.
// Agora, mais de um candidato no nível parcial vira Ambiguous,
// e nível 3 (substring de pacote, o mais fraco) vira WeakMatch —
// quem chama decide se pede confirmação nesses dois casos.
// ============================================================

package com.example.jarvis.core

data class LaunchableApp(val packageName: String, val label: String)

sealed class AppMatchResult {
    /** Match exato de label — sem ambiguidade, seguro executar direto. */
    data class Exact(val app: LaunchableApp) : AppMatchResult()

    /** Mais de um app bateu no mesmo nível de confiança — não dá pra escolher sozinho. */
    data class Ambiguous(val candidates: List<LaunchableApp>) : AppMatchResult()

    /** Só achou via substring de pacote (nível mais fraco) — vale confirmar antes de abrir. */
    data class WeakMatch(val app: LaunchableApp) : AppMatchResult()

    object NoMatch : AppMatchResult()
}

object AppMatcher {

    /**
     * Resolve o nome dito para o(s) app(s) candidato(s), em 3 níveis
     * de confiança. Nunca escolhe silenciosamente entre múltiplos
     * candidatos do mesmo nível — isso é responsabilidade de quem chama.
     */
    fun resolve(spokenName: String, installedApps: List<LaunchableApp>): AppMatchResult {
        val target = normalize(spokenName)
        if (target.isBlank()) return AppMatchResult.NoMatch

        // 1. Match exato de label (ex: "whatsapp" -> label "WhatsApp")
        val exactMatches = installedApps.filter { normalize(it.label) == target }
        if (exactMatches.size == 1) return AppMatchResult.Exact(exactMatches.first())
        if (exactMatches.size > 1) return AppMatchResult.Ambiguous(exactMatches)

        // 2. Match parcial de label (ex: "mensagens" -> "Mensagens" E "Google Mensagens")
        val partialMatches = installedApps.filter { normalize(it.label).contains(target) }
        if (partialMatches.size == 1) return AppMatchResult.Exact(partialMatches.first())
        if (partialMatches.size > 1) return AppMatchResult.Ambiguous(partialMatches)

        // 3. Match por substring no nome do pacote — o mais fraco, sempre WeakMatch
        val packageMatches = installedApps.filter { it.packageName.lowercase().contains(target) }
        if (packageMatches.size == 1) return AppMatchResult.WeakMatch(packageMatches.first())
        if (packageMatches.size > 1) return AppMatchResult.Ambiguous(packageMatches)

        return AppMatchResult.NoMatch
    }

    fun normalize(s: String): String {
        val semAcentos = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        return semAcentos.lowercase().trim().replace(Regex("[^a-z0-9]"), "")
    }
}
