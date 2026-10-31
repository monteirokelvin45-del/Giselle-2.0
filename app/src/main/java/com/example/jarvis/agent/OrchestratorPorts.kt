// ============================================================
// agent/OrchestratorPorts.kt
// ------------------------------------------------------------
// Interfaces que o AgentOrchestrator depende, em vez de classes
// concretas. Isso não é só "para testar" — desacopla o
// orquestrador do singleton estático JarvisAccessibilityService
// .instance, que era um acoplamento frágil por si só (o serviço
// pode não estar conectado ainda quando o orquestrador tenta agir).
//
// As implementações reais destas interfaces ficam nas classes
// concretas já existentes (ClaudeClient, MemoryStore, AuditLog,
// AppLauncher, JarvisAccessibilityService via um adapter fino).
// Em teste, usamos fakes em memória.
// ============================================================

package com.example.jarvis.agent

import com.example.jarvis.core.*
import com.example.jarvis.llm.DecisionResult

interface DecisionMaker {
    suspend fun decide(context: PerceptionContext): DecisionResult
    fun fallbackDecision(reason: String): AgentDecision
}

interface MemoryPort {
    suspend fun recall(query: String, topK: Int = 5): List<MemoryEntry>
    suspend fun remember(content: String, category: MemoryCategory)
}

interface AuditPort {
    suspend fun record(
        app: String,
        userCommand: String?,
        decision: AgentDecision,
        riskLevel: RiskLevel,
        outcome: ActionOutcome,
        errorMessage: String? = null
    )
}

interface AppLauncherPort {
    fun openByName(name: String): Boolean
}

/**
 * Abstrai a execução de ações na tela — implementado por um
 * adapter fino em cima de JarvisAccessibilityService.instance na
 * classe real, e por um fake controlável em teste.
 */
interface ScreenActuator {
    fun click(target: String): Boolean
    fun typeText(text: String): Boolean
    fun typeText(text: String, target: String?): Boolean = typeText(text)
    fun goBack(): Boolean
    fun goHome(): Boolean
    fun tapAt(x: Float, y: Float): Boolean
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 350): Boolean = false
    /** false quando não há serviço de Accessibility conectado no momento. */
    fun isAvailable(): Boolean
}
