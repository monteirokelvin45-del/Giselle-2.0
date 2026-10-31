// ============================================================
// test/agent/AgentOrchestratorTest.kt
// ------------------------------------------------------------
// Testa o fluxo completo do orquestrador com fakes em memória
// para DecisionMaker, MemoryPort, AuditPort, AppLauncherPort e
// ScreenActuator — nenhuma dependência de Android real.
// ============================================================

package com.example.jarvis.agent

import com.example.jarvis.core.*
import com.example.jarvis.llm.DecisionResult
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FakeDecisionMaker : DecisionMaker {
    var nextResult: DecisionResult = DecisionResult.Success(
        AgentDecision(
            reasoning = "teste", actionType = ActionType.CLICK, target = "OK",
            requiresConfirmation = false, userFacingSummary = "Clicando em OK"
        )
    )
    var decideCallCount = 0

    override suspend fun decide(context: PerceptionContext): DecisionResult {
        decideCallCount++
        return nextResult
    }

    override fun fallbackDecision(reason: String): AgentDecision = AgentDecision(
        reasoning = "Fallback: $reason", actionType = ActionType.ASK_USER,
        requiresConfirmation = false, userFacingSummary = "Não consegui pensar nisso agora."
    )
}

class FakeMemoryPort : MemoryPort {
    val remembered = mutableListOf<Pair<String, MemoryCategory>>()
    override suspend fun recall(query: String, topK: Int): List<MemoryEntry> = emptyList()
    override suspend fun remember(content: String, category: MemoryCategory) {
        remembered.add(content to category)
    }
}

class FakeAuditPort : AuditPort {
    val records = mutableListOf<ActionOutcome>()
    override suspend fun record(
        app: String, userCommand: String?, decision: AgentDecision,
        riskLevel: RiskLevel, outcome: ActionOutcome, errorMessage: String?
    ) {
        records.add(outcome)
    }
}

class FakeAppLauncherPort(private val shouldSucceed: Boolean = true) : AppLauncherPort {
    var lastRequestedName: String? = null
    override fun openByName(name: String): Boolean {
        lastRequestedName = name
        return shouldSucceed
    }
}

class FakeScreenActuator(private val available: Boolean = true) : ScreenActuator {
    var lastClickTarget: String? = null
    var lastSwipe: List<Float>? = null
    var tapCount = 0
    override fun click(target: String): Boolean { lastClickTarget = target; return true }
    override fun typeText(text: String): Boolean = true
    override fun goBack(): Boolean = true
    override fun goHome(): Boolean = true
    override fun tapAt(x: Float, y: Float): Boolean { tapCount++; return true }
    override fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        lastSwipe = listOf(x1, y1, x2, y2)
        return true
    }
    override fun isAvailable(): Boolean = available
}

class AgentOrchestratorTest {

    private lateinit var claude: FakeDecisionMaker
    private lateinit var memory: FakeMemoryPort
    private lateinit var audit: FakeAuditPort
    private lateinit var appLauncher: FakeAppLauncherPort
    private lateinit var screen: FakeScreenActuator
    private var confirmationsAsked = mutableListOf<PendingAction>()
    private var spokenMessages = mutableListOf<String>()
    private var circuitOpenMessages = mutableListOf<String>()

    private fun buildOrchestrator(
        rateLimiter: RateLimiter = RateLimiter(maxCallsPerWindow = 20, windowMs = 60_000L),
        circuitBreaker: CircuitBreaker = CircuitBreaker(failureThreshold = 5, openDurationMs = 60_000L),
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        screenAvailable: Boolean = true
    ): AgentOrchestrator {
        claude = FakeDecisionMaker()
        memory = FakeMemoryPort()
        audit = FakeAuditPort()
        appLauncher = FakeAppLauncherPort()
        screen = FakeScreenActuator(available = screenAvailable)
        confirmationsAsked = mutableListOf()
        spokenMessages = mutableListOf()
        circuitOpenMessages = mutableListOf()

        return AgentOrchestrator(
            claude = claude, memory = memory, auditLog = audit,
            appLauncher = appLauncher, screen = screen,
            rateLimiter = rateLimiter, circuitBreaker = circuitBreaker,
            onNeedsConfirmation = { confirmationsAsked.add(it) },
            onSpeak = { spokenMessages.add(it) },
            onCircuitOpen = { circuitOpenMessages.add(it) },
            dispatcher = dispatcher
        )
    }

    @Test
    fun `acao segura e executada diretamente sem pedir confirmacao`() = runTest {
        val orchestrator = buildOrchestrator(dispatcher = StandardTestDispatcher(testScheduler))
        claude.nextResult = DecisionResult.Success(
            AgentDecision(
                reasoning = "abrir configurações", actionType = ActionType.CLICK, target = "Configurações",
                requiresConfirmation = false, userFacingSummary = "Abrindo configurações"
            )
        )

        orchestrator.process("abre configurações", "com.android.settings", "tela com botão Configurações").join()

        assertEquals("Configurações", screen.lastClickTarget)
        assertTrue(confirmationsAsked.isEmpty())
        assertEquals(listOf(ActionOutcome.SUCCESS), audit.records)
    }

    @Test
    fun `swipe executa gesto de arrasto sem converter para toque`() = runTest {
        val orchestrator = buildOrchestrator(dispatcher = StandardTestDispatcher(testScheduler))
        claude.nextResult = DecisionResult.Success(
            AgentDecision(
                reasoning = "deslizar", actionType = ActionType.SWIPE,
                coordinates = Coordinates(12f, 24f),
                endCoordinates = Coordinates(180f, 240f),
                requiresConfirmation = false,
                userFacingSummary = "Deslizando"
            )
        )

        orchestrator.process("desliza para baixo", "com.example.reader", "").join()

        assertEquals(listOf(12f, 24f, 180f, 240f), screen.lastSwipe)
        assertEquals(0, screen.tapCount)
    }

    @Test
    fun `acao sensivel vai para fila de confirmacao e nao executa direto`() = runTest {
        val orchestrator = buildOrchestrator(dispatcher = StandardTestDispatcher(testScheduler))
        claude.nextResult = DecisionResult.Success(
            AgentDecision(
                reasoning = "enviar mensagem", actionType = ActionType.CLICK, target = "Enviar",
                requiresConfirmation = false,
                userFacingSummary = "Vou enviar a mensagem"
            )
        )

        orchestrator.process("manda a mensagem", "com.whatsapp", "tela com botão Enviar").join()

        assertNull(screen.lastClickTarget)
        assertEquals(1, confirmationsAsked.size)
        assertEquals(listOf(ActionOutcome.PENDING_CONFIRMATION), audit.records)
    }

    @Test
    fun `confirmar uma acao pendente executa e loga sucesso`() = runTest {
        val orchestrator = buildOrchestrator(dispatcher = StandardTestDispatcher(testScheduler))
        claude.nextResult = DecisionResult.Success(
            AgentDecision(
                reasoning = "enviar", actionType = ActionType.CLICK, target = "Enviar",
                requiresConfirmation = false, userFacingSummary = "Vou enviar a mensagem"
            )
        )
        orchestrator.process("manda", "com.whatsapp", "tela").join()
        val pending = confirmationsAsked.first()

        orchestrator.resolveConfirmation(pending, approved = true).join()

        assertEquals("Enviar", screen.lastClickTarget)
        assertTrue(audit.records.contains(ActionOutcome.SUCCESS))
    }

    @Test
    fun `rejeitar confirmacao vira memoria de correcao e nao executa`() = runTest {
        val orchestrator = buildOrchestrator(dispatcher = StandardTestDispatcher(testScheduler))
        claude.nextResult = DecisionResult.Success(
            AgentDecision(
                reasoning = "enviar", actionType = ActionType.CLICK, target = "Enviar",
                requiresConfirmation = false, userFacingSummary = "Vou enviar a mensagem"
            )
        )
        orchestrator.process("manda", "com.whatsapp", "tela").join()
        val pending = confirmationsAsked.first()

        orchestrator.resolveConfirmation(pending, approved = false).join()

        assertNull(screen.lastClickTarget)
        assertTrue(audit.records.contains(ActionOutcome.CANCELLED_BY_USER))
        assertEquals(1, memory.remembered.size)
        assertEquals(MemoryCategory.CORRECTION, memory.remembered.first().second)
    }

    @Test
    fun `open_app usa o app launcher em vez do screen actuator`() = runTest {
        val orchestrator = buildOrchestrator(dispatcher = StandardTestDispatcher(testScheduler))
        claude.nextResult = DecisionResult.Success(
            AgentDecision(
                reasoning = "abrir spotify", actionType = ActionType.OPEN_APP, target = "Spotify",
                requiresConfirmation = false, userFacingSummary = "Abrindo o Spotify"
            )
        )

        orchestrator.process("abre o spotify", "com.android.launcher", "").join()

        assertEquals("Spotify", appLauncher.lastRequestedName)
        assertNull(screen.lastClickTarget)
    }

    @Test
    fun `rate limiter bloqueia quando o limite da janela e atingido`() = runTest {
        val orchestrator = buildOrchestrator(
            rateLimiter = RateLimiter(maxCallsPerWindow = 1, windowMs = 60_000L),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        orchestrator.process("primeiro comando", "com.example", "").join()
        orchestrator.process("segundo comando", "com.example", "").join()

        assertEquals(1, claude.decideCallCount)
        assertTrue(spokenMessages.any { it.contains("calma") })
    }

    @Test
    fun `circuit breaker aberto impede novas chamadas ao LLM`() = runTest {
        val breaker = CircuitBreaker(failureThreshold = 1, openDurationMs = 60_000L)
        val orchestrator = buildOrchestrator(circuitBreaker = breaker, dispatcher = StandardTestDispatcher(testScheduler))

        claude.nextResult = DecisionResult.Failure("erro simulado", recoverable = true)
        orchestrator.process("comando 1", "com.example", "").join()

        orchestrator.process("comando 2", "com.example", "").join()

        assertEquals(1, claude.decideCallCount)
        assertTrue(circuitOpenMessages.isNotEmpty())
    }

    @Test
    fun `falha do LLM aciona fallback seguro e registra no audit log`() = runTest {
        val orchestrator = buildOrchestrator(dispatcher = StandardTestDispatcher(testScheduler))
        claude.nextResult = DecisionResult.Failure("timeout simulado", recoverable = true)

        orchestrator.process("qualquer comando", "com.example", "").join()

        assertTrue(spokenMessages.isNotEmpty())
        assertEquals(listOf(ActionOutcome.FAILED), audit.records)
        assertNull(screen.lastClickTarget)
    }

    @Test
    fun `acao bem sucedida vira memoria de longo prazo`() = runTest {
        val orchestrator = buildOrchestrator(dispatcher = StandardTestDispatcher(testScheduler))
        claude.nextResult = DecisionResult.Success(
            AgentDecision(
                reasoning = "voltar", actionType = ActionType.GO_BACK,
                requiresConfirmation = false, userFacingSummary = "Voltando"
            )
        )

        orchestrator.process("volta", "com.example", "").join()

        assertEquals(1, memory.remembered.size)
        assertEquals(MemoryCategory.APP_BEHAVIOR, memory.remembered.first().second)
    }

    @Test
    fun `screen indisponivel resulta em falha para acoes de tela`() = runTest {
        val orchestrator = buildOrchestrator(
            dispatcher = StandardTestDispatcher(testScheduler),
            screenAvailable = false
        )
        claude.nextResult = DecisionResult.Success(
            AgentDecision(
                reasoning = "clicar", actionType = ActionType.CLICK, target = "Qualquer",
                requiresConfirmation = false, userFacingSummary = "Clicando"
            )
        )

        orchestrator.process("clica ai", "com.example", "").join()

        assertEquals(listOf(ActionOutcome.FAILED), audit.records)
    }

    @Test
    fun `resposta apenas informativa nao gera memoria nem exige tela disponivel`() = runTest {
        val orchestrator = buildOrchestrator(
            dispatcher = StandardTestDispatcher(testScheduler),
            screenAvailable = false
        )
        claude.nextResult = DecisionResult.Success(
            AgentDecision(
                reasoning = "responder direto", actionType = ActionType.ANSWER_ONLY,
                requiresConfirmation = false, userFacingSummary = "A resposta é 42."
            )
        )

        orchestrator.process("quanto é a resposta?", "com.example", "").join()

        assertTrue(memory.remembered.isEmpty())
        assertTrue(spokenMessages.contains("A resposta é 42."))
    }
}
