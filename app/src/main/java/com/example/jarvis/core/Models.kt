// ============================================================
// core/Models.kt
// Contratos de dados que atravessam todas as camadas do sistema.
// Isso é o "idioma comum" entre Percepção -> Cérebro -> Ação.
// ============================================================

package com.example.jarvis.core

import kotlinx.serialization.Serializable

// -----------------------------------------------------
// O que o cérebro (LLM) recebe como entrada de contexto
// -----------------------------------------------------
data class PerceptionContext(
    val currentApp: String,              // pacote em foco, ex: "com.whatsapp"
    val screenText: String,              // snapshot textual da árvore de tela
    val userCommand: String?,            // comando explícito do usuário, se houver
    val recentHistory: List<ActionLogEntry>, // últimas N ações (memória de curto prazo)
    val relevantMemories: List<MemoryEntry>  // recuperadas do banco vetorial (longo prazo)
)

// -----------------------------------------------------
// O que o cérebro (LLM) devolve como decisão
// Isso é o contrato JSON que forçamos o LLM a respeitar
// -----------------------------------------------------
@Serializable
data class AgentDecision(
    val reasoning: String,           // por que decidiu isso (log/auditoria, nunca mostrado cru ao user)
    val actionType: ActionType,
    val target: String? = null,      // texto do elemento alvo, ex: "Enviar"
    val inputText: String? = null,   // texto a digitar, se aplicável
    val coordinates: Coordinates? = null,
    val endCoordinates: Coordinates? = null,
    val requiresConfirmation: Boolean, // decidido pela SafetyGate, não pelo LLM
    val userFacingSummary: String    // frase curta pra mostrar/falar pro usuário
)

@Serializable
enum class ActionType {
    CLICK, TYPE_TEXT, SWIPE, OPEN_APP, GO_BACK, GO_HOME,
    WAIT, ASK_USER, ANSWER_ONLY, NO_ACTION,
    // Fase 1 (Giselle): ações via APIs nativas, sem depender de acessibilidade
    CALL, SEND_SMS, SET_ALARM, SET_TIMER, OPEN_URL, SET_VOLUME,
    // Fases 5/6: mensagem via app (receitas) e rotinas diárias
    APP_MESSAGE, SET_ROUTINE,
    // Lote 1: notificações/chamadas, resumo, gastos, visão, memória, skills, aparência
    READ_NOTIFICATIONS, REPLY_NOTIFICATION, ANSWER_CALL, REJECT_CALL, DAILY_BRIEF, LOG_EXPENSE, EXPENSE_REPORT,
    DESCRIBE_SCREEN, REMEMBER, RUN_SKILL, LEARN_SKILL, SET_SKIN,
    // Moçambique: dinheiro móvel (só abre o discador, nunca digita PIN) e modo estudo (foco com temporizador)
    MOBILE_MONEY, STUDY_MODE
}

@Serializable
data class Coordinates(val x: Float, val y: Float)

// -----------------------------------------------------
// Memória de curto prazo (log de ações já tomadas)
// -----------------------------------------------------
data class ActionLogEntry(
    val timestamp: Long,
    val app: String,
    val command: String?,
    val decision: AgentDecision,
    val outcome: ActionOutcome
)

enum class ActionOutcome { SUCCESS, FAILED, CANCELLED_BY_USER, PENDING_CONFIRMATION }

// -----------------------------------------------------
// Memória de longo prazo (aprendizado persistente)
// -----------------------------------------------------
data class MemoryEntry(
    val id: String,
    val content: String,        // ex: "Usuário sempre confirma pedido no iFood antes de finalizar"
    val category: MemoryCategory,
    val embedding: FloatArray? = null,
    val createdAt: Long,
    val lastReinforced: Long,
    val confidence: Float       // cresce com repetição, decai com contradição
)

enum class MemoryCategory {
    PREFERENCE,     // gostos e hábitos
    ROUTINE,        // padrões de comportamento repetidos
    APP_BEHAVIOR,   // "como esse app específico funciona"
    CORRECTION,     // vezes que o usuário corrigiu uma decisão errada (peso alto!)
    FACT            // fatos objetivos sobre o usuário
}

// -----------------------------------------------------
// Classificação de risco por app/ação (usado pela SafetyGate)
// -----------------------------------------------------
enum class RiskLevel { SAFE, SENSITIVE, IRREVERSIBLE }
