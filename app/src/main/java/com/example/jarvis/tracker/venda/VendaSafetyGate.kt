// ============================================================
// tracker/venda/VendaSafetyGate.kt
// ------------------------------------------------------------
// ESQUELETO — nenhuma integração real com Hotmart/Kiwify/Fiverr
// existe ainda. Isso é só a estrutura de decisão, seguindo o
// mesmo princípio do SafetyGate/TrackerSafetyGate: qualquer
// automação futura de vendas/monetização NUNCA pode agir sozinha
// em ações que envolvem dinheiro real, comunicação com clientes,
// ou publicação pública — sempre confirmação explícita.
//
// Diferente do SafetyGate do núcleo (ações de tela) e do
// TrackerSafetyGate (suficiência de dados), este gate classifica
// AÇÕES DE NEGÓCIO hipotéticas — qualquer integração futura com
// uma plataforma de venda passaria por aqui antes de executar
// qualquer coisa.
//
// NADA aqui se conecta a uma API real. VendaAction é um modelo
// de intenção que uma futura camada de integração produziria.
// ============================================================

package com.example.jarvis.tracker.venda

enum class VendaActionType {
    RESPONDER_MENSAGEM_CLIENTE,
    ALTERAR_PRECO,
    PUBLICAR_PRODUTO,
    DESPUBLICAR_PRODUTO,
    EMITIR_REEMBOLSO,
    RESPONDER_AVALIACAO,
    ENVIAR_PROPOSTA, // ex: candidatura no Fiverr
    CONSULTAR_METRICAS // única ação sem risco — só leitura
}

data class VendaAction(
    val tipo: VendaActionType,
    val plataforma: String, // "hotmart" | "kiwify" | "fiverr" | outra
    val valorEnvolvido: Double? = null, // preço, valor de reembolso etc. — null se não aplicável
    val descricao: String
)

sealed class VendaRiskLevel {
    /** Só leitura — consultar métricas, ver pedidos. Autonomia total. */
    object Safe : VendaRiskLevel()

    /** Envolve comunicação com um cliente real, mas não dinheiro nem publicação. */
    object Sensitive : VendaRiskLevel()

    /** Envolve dinheiro, preço, ou publicação pública — sempre bloqueado sem confirmação. */
    object Blocked : VendaRiskLevel()
}

object VendaSafetyGate {

    /**
     * Classifica a ação. Assim como o SafetyGate do núcleo, esta
     * decisão NUNCA deve vir de um LLM se autoavaliando — é hard-coded
     * de propósito. Qualquer integração futura chama isto ANTES de
     * executar qualquer ação de venda.
     */
    fun evaluate(action: VendaAction): VendaRiskLevel = when (action.tipo) {
        VendaActionType.CONSULTAR_METRICAS ->
            VendaRiskLevel.Safe

        VendaActionType.RESPONDER_MENSAGEM_CLIENTE,
        VendaActionType.RESPONDER_AVALIACAO ->
            VendaRiskLevel.Sensitive

        VendaActionType.ALTERAR_PRECO,
        VendaActionType.PUBLICAR_PRODUTO,
        VendaActionType.DESPUBLICAR_PRODUTO,
        VendaActionType.EMITIR_REEMBOLSO,
        VendaActionType.ENVIAR_PROPOSTA ->
            VendaRiskLevel.Blocked
    }

    /**
     * true = a ação pode prosseguir sem intervenção humana.
     * false = precisa de confirmação explícita antes de executar,
     * mesmo que uma futura IA "ache" que está certa.
     */
    fun canProceedAutonomously(action: VendaAction): Boolean =
        evaluate(action) == VendaRiskLevel.Safe
}
