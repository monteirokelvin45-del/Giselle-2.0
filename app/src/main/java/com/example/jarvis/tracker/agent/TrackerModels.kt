// ============================================================
// tracker/agent/TrackerModels.kt (modelos de agente, não confundir
// com tracker/data/TrackerModels.kt que são as entidades Room)
// ------------------------------------------------------------
// Contrato entre o TrackerOrchestrator e o Claude, no mesmo
// espírito do PerceptionContext/AgentDecision do núcleo — mas
// aqui a "percepção" é o histórico de dados, não a tela.
// ============================================================

package com.example.jarvis.tracker.agent

import kotlinx.serialization.Serializable

/** Snapshot agregado do estado atual do usuário, montado a partir do TrackerDatabase. */
data class TrackerContext(
    val perfilResumo: String,       // ex: "objetivo: hipertrofia, nível: intermediário"
    val metasResumo: String,
    val janelaRecente: String,      // resumo textual dos últimos N dias de registros
    val eventosSemFeedback: List<String> // sugestões anteriores ainda sem reação do usuário
)

@Serializable
enum class TrackerInsightType {
    INSIGHT_PESO, INSIGHT_TREINO, INSIGHT_ESTUDO, INSIGHT_FINANCAS,
    INSIGHT_CONSISTENCIA, DIAGNOSTICO_GERAL,
    DESCONHECIDO // fallback explícito — nunca cai num branch silencioso sem log
}

/**
 * Diferente do AgentDecision (que executa ação na tela), aqui o
 * resultado é sempre um INSIGHT/SUGESTÃO — nunca uma escrita direta
 * nos dados do usuário. Vira uma linha em ai_events, nada mais.
 */
@Serializable
data class TrackerInsight(
    val tipo: TrackerInsightType, // enum fechado — um typo do LLM vira DESCONHECIDO, nunca passa despercebido
    val dadosObservados: String, // resumo do que motivou o insight (vai pro ai_events.dadosObservadosJson)
    val sugestaoTexto: String,  // o texto que o usuário vê
    val prioridade: TrackerInsightPrioridade
)

@Serializable
enum class TrackerInsightPrioridade { BAIXA, MEDIA, ALTA }
