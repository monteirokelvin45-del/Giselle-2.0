// ============================================================
// agent/OrchestratorAdapters.kt
// ------------------------------------------------------------
// Implementações reais das portas do orquestrador, em cima das
// classes concretas já existentes. Nenhuma lógica nova aqui —
// só encaixe de interface.
// ============================================================

package com.example.jarvis.agent

import com.example.jarvis.accessibility.JarvisAccessibilityService
import com.example.jarvis.core.*
import com.example.jarvis.llm.ClaudeClient
import com.example.jarvis.llm.DecisionResult
import com.example.jarvis.memory.MemoryStore

class ClaudeDecisionMaker(private val client: ClaudeClient) : DecisionMaker {
    override suspend fun decide(context: PerceptionContext): DecisionResult = client.decide(context)
    override fun fallbackDecision(reason: String): AgentDecision = client.fallbackDecision(reason)
}

class RoomMemoryPort(private val store: MemoryStore) : MemoryPort {
    override suspend fun recall(query: String, topK: Int): List<MemoryEntry> = store.recall(query, topK)
    override suspend fun remember(content: String, category: MemoryCategory) = store.remember(content, category)
}

class RoomAuditPort(private val log: AuditLog) : AuditPort {
    override suspend fun record(
        app: String, userCommand: String?, decision: AgentDecision,
        riskLevel: RiskLevel, outcome: ActionOutcome, errorMessage: String?
    ) = log.record(app, userCommand, decision, riskLevel, outcome, errorMessage)
}

class DefaultAppLauncherPort(private val launcher: AppLauncher) : AppLauncherPort {
    override fun openByName(name: String): Boolean = launcher.openByName(name)
}

/**
 * Adapter fino sobre o singleton estático do AccessibilityService.
 * Isolar esse acesso aqui (em vez de espalhar JarvisAccessibilityService
 * .instance pelo orquestrador) é o que torna o resto testável.
 */
class AccessibilityScreenActuator : ScreenActuator {
    override fun click(target: String): Boolean =
        JarvisAccessibilityService.instance?.clickByText(target) ?: false

    override fun typeText(text: String): Boolean =
        JarvisAccessibilityService.instance?.typeText(text) ?: false

    override fun typeText(text: String, target: String?): Boolean =
        JarvisAccessibilityService.instance?.typeText(text, target) ?: false

    override fun goBack(): Boolean =
        JarvisAccessibilityService.instance?.goBack() ?: false

    override fun goHome(): Boolean =
        JarvisAccessibilityService.instance?.goHome() ?: false

    override fun tapAt(x: Float, y: Float): Boolean =
        JarvisAccessibilityService.instance?.tapAt(x, y) ?: false

    override fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean =
        JarvisAccessibilityService.instance?.swipeAt(x1, y1, x2, y2, durationMs) ?: false

    override fun isAvailable(): Boolean = JarvisAccessibilityService.instance != null
}
