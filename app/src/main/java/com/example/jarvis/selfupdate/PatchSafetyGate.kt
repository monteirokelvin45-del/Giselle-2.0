// ============================================================
// selfupdate/PatchSafetyGate.kt
// ------------------------------------------------------------
// Mesma filosofia do SafetyGate/TrackerSafetyGate/VendaSafetyGate:
// a regra que decide fica em código, fora do alcance do LLM.
//
// Aqui a regra é simples e não tem exceção configurável:
// NENHUM patch é aplicado automaticamente, nunca, independente
// da "confiança" que a própria IA atribuiu ao diagnóstico.
// Confiança alta muda a ORDEM de exibição na tela de revisão
// (patches de alta confiança aparecem primeiro), nunca pula a
// revisão em si.
// ============================================================

package com.example.jarvis.selfupdate

object PatchSafetyGate {
    /**
     * Sempre true, de propósito — existe como função (não como
     * constante) pra deixar claro no ponto de chamada que isso
     * é uma decisão verificada, não um esquecimento de checagem.
     */
    fun requerAprovacaoHumana(patch: PatchSuggestionEntity): Boolean = true

    /**
     * Patches que mexem em arquivos de segurança não podem nem
     * ser sugeridos como "alta confiança" — mudança em SafetyGate,
     * TrackerSafetyGate, VendaSafetyGate ou AuditLog é sempre
     * rebaixada pra "baixa", forçando leitura mais cuidadosa sua.
     */
    private val arquivosCriticos = setOf(
        "SafetyGate.kt", "TrackerSafetyGate.kt", "VendaSafetyGate.kt",
        "PatchSafetyGate.kt", "AuditLog.kt"
    )

    fun confiancaEfetiva(patch: PatchSuggestionEntity): String {
        val tocaArquivoCritico = arquivosCriticos.any { patch.arquivoAlvo.endsWith(it) }
        return if (tocaArquivoCritico) "baixa" else patch.confiancaDiagnostico
    }
}
