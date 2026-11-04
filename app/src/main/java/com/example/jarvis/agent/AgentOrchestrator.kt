// ============================================================
// agent/AgentOrchestrator.kt
// ------------------------------------------------------------
// O maestro. Conecta:
//   Percepção (AccessibilityService, via ScreenActuator)
//        -> RateLimiter + CircuitBreaker (proteção)
//        -> Memória (recall, via MemoryPort)
//        -> LLM (decide, via DecisionMaker, com timeout/retry/fallback)
//        -> SafetyGate (valida)
//        -> Execução (ScreenActuator / AppLauncherPort) OU fila de confirmação
//        -> AuditPort (registro bruto e imutável)
//        -> Log (memória de curto prazo + aprendizado)
//
// Depende de INTERFACES (agent/OrchestratorPorts.kt), não de
// classes concretas — é o que permite testar toda a lógica de
// decisão/proteção sem precisar de Android real. As implementações
// de produção ficam em agent/OrchestratorAdapters.kt.
// ============================================================

package com.example.jarvis.agent

import com.example.jarvis.core.*
import com.example.jarvis.vision.VisionPort
import com.example.jarvis.skills.SkillRunner
import com.example.jarvis.skills.SkillStore
import com.example.jarvis.notifications.NotificationInbox
import com.example.jarvis.routine.DailyBrief
import com.example.jarvis.ledger.Ledger
import com.example.jarvis.llm.DecisionResult
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

class AgentOrchestrator(
    private val claude: DecisionMaker,
    private val memory: MemoryPort,
    private val auditLog: AuditPort,
    private val appLauncher: AppLauncherPort,
    private val screen: ScreenActuator,
    private val rateLimiter: RateLimiter,
    private val circuitBreaker: CircuitBreaker,
    private val onNeedsConfirmation: (PendingAction) -> Unit,
    private val onSpeak: (String) -> Unit,
    private val onCircuitOpen: (String) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    // Fase 1/2 (Giselle): executor nativo e leitor de tela p/ o loop multi-passo.
    private val intents: IntentPort? = null,
    private val screenReader: (() -> Pair<String, String>)? = null,
    private val maxSteps: Int = 8,
    private val visionPort: VisionPort? = null,
    private val appContext: android.content.Context? = null,
    private val onHealthyCycle: (() -> Unit)? = null
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    /** Pesquisa na web com fontes; ligada no serviço. Sem ela, WEB_SEARCH falha com segurança. */
    var webSearchPort: WebSearchPort? = null
    private val skillRunner by lazy { SkillRunner(appLauncher, screen, visionPort) }
    private val messenger by lazy { AppMessenger(appLauncher, screen, visionPort) }
    private val shortTermHistory = ArrayDeque<ActionLogEntry>()
    private val maxHistorySize = 20
    private val pendingQueue = ConcurrentLinkedQueue<PendingAction>()

    /** Exposto só para teste inspecionar a fila sem precisar de um segundo canal. */
    fun pendingSnapshot(): List<PendingAction> = pendingQueue.toList()

    /** Exposto só para teste inspecionar o histórico curto sem duplicar estado no fake. */
    fun historySnapshot(): List<ActionLogEntry> = shortTermHistory.toList()

    /**
     * Ponto de entrada principal. Chamado:
     * (a) quando o usuário dá um comando explícito (texto/voz), OU
     * (b) periodicamente pelo modo autônomo observando a tela.
     *
     * Retorna o Job da coroutine disparada — em produção isso é
     * fire-and-forget; em teste, dá pra aguardar (job.join()) antes
     * de fazer as asserções.
     */
    fun process(userCommand: String?, currentApp: String, screenSnapshot: String): Job {
        return scope.launch {
            // Item 7a: circuit breaker — se o sistema andou falhando muito
            // em sequência, para de tentar por um tempo em vez de insistir
            // agindo errado repetidamente numa tela sensível.
            if (!circuitBreaker.allowRequest()) {
                onCircuitOpen("muitas falhas recentes, tentando novamente em instantes")
                return@launch
            }

            // Item 7b: rate limiter — protege contra loop rápido demais
            // (custo de API disparado + ações em sequência descontrolada).
            if (!rateLimiter.tryAcquire()) {
                onSpeak("Estou processando muita coisa agora, vamos com calma — tenta de novo em um minuto.")
                return@launch
            }

            try {
                val relevantMemories = memory.recall(
                    query = userCommand ?: "contexto do app $currentApp",
                    topK = 5
                )

                val context = PerceptionContext(
                    currentApp = currentApp,
                    screenText = screenSnapshot,
                    userCommand = userCommand,
                    recentHistory = shortTermHistory.toList(),
                    relevantMemories = relevantMemories
                )

                // Item 10: chamada com timeout/retry embutidos no DecisionMaker,
                // resultado explícito em vez de exceção crua.
                when (val result = claude.decide(context)) {
                    is DecisionResult.Success -> {
                        circuitBreaker.recordSuccess()
                        runCatching { onHealthyCycle?.invoke() }
                        val safeDecision = SafetyGate.apply(result.decision, currentApp)
                        handleDecision(safeDecision, context)
                    }
                    is DecisionResult.Failure -> {
                        circuitBreaker.recordFailure()
                        val fallback = claude.fallbackDecision(result.reason)
                        auditLog.record(
                            app = currentApp, userCommand = userCommand, decision = fallback,
                            riskLevel = RiskLevel.SAFE, outcome = ActionOutcome.FAILED,
                            errorMessage = result.reason
                        )
                        onSpeak(fallback.userFacingSummary)
                    }
                }

            } catch (e: Exception) {
                circuitBreaker.recordFailure()
                onSpeak("Tive um problema pra processar isso: ${e.message ?: "erro desconhecido"}")
            }
        }
    }

    private suspend fun handleDecision(decision: AgentDecision, context: PerceptionContext) {
        val risk = SafetyGate.evaluate(decision, context.currentApp)

        if (decision.requiresConfirmation) {
            val pending = PendingAction(decision, context.currentApp)
            pendingQueue.add(pending)
            onNeedsConfirmation(pending)
            logAction(decision, context, ActionOutcome.PENDING_CONFIRMATION, risk)
            return
        }

        val outcome = execute(decision)
        logAction(decision, context, outcome, risk)

        if (decision.userFacingSummary.isNotBlank() &&
            decision.actionType != ActionType.NO_ACTION
        ) {
            onSpeak(decision.userFacingSummary)
        }
    }

    /**
     * Chamado pela UI quando o usuário confirma ou rejeita uma ação pendente.
     * Rejeições viram memória CORRECTION — é aqui que o sistema aprende
     * de forma mais forte, com peso de confiança elevado.
     */
    /**
     * Fase 2: loop planejar → agir → verificar → replanejar.
     * Repete até o LLM responder ANSWER_ONLY/ASK_USER/NO_ACTION, pedir confirmação,
     * falhar 2x seguidas ou estourar [maxSteps]. O histórico (com FAILED) vai no
     * prompt, então o replanejamento acontece naturalmente.
     */
    fun processTask(userCommand: String, initialApp: String, initialScreen: String): Job {
        return scope.launch {
            if (!circuitBreaker.allowRequest()) {
                onCircuitOpen("muitas falhas recentes, tentando novamente em instantes")
                return@launch
            }
            // Atalho: skill ensinada cujo gatilho aparece no comando → corre sem gastar LLM.
            val direct = appContext?.let { SkillStore.match(it, userCommand) }
            if (direct != null) {
                if (!rateLimiter.tryAcquire()) {
                    onSpeak("Estou processando muita coisa agora — tenta de novo em um minuto.")
                    return@launch
                }
                val decision = AgentDecision(
                    reasoning = "Skill salva selecionada pelo comando explícito do usuário.",
                    actionType = ActionType.RUN_SKILL,
                    target = direct.name,
                    inputText = userCommand,
                    requiresConfirmation = true,
                    userFacingSummary = "Executar a skill ${direct.name}?"
                )
                val pending = PendingAction(decision, initialApp)
                pendingQueue.add(pending)
                onNeedsConfirmation(pending)
                return@launch
            }
            val limit = if (isLong(userCommand)) 40 else maxSteps
            val failLimit = if (isLong(userCommand)) 4 else 2
            var app = initialApp
            var screenText = initialScreen
            var consecutiveFails = 0
            GiselleBus.taskActive = true
            GiselleBus.set(GiselleState.THINKING)
            try {
                for (step in 1..limit) {
                    if (!rateLimiter.tryAcquire()) {
                        onSpeak("Estou processando muita coisa agora — tenta de novo em um minuto.")
                        return@launch
                    }
                    val context = PerceptionContext(
                        currentApp = app,
                        screenText = screenText,
                        userCommand = userCommand,
                        recentHistory = shortTermHistory.toList(),
                        relevantMemories = memory.recall(userCommand, 5)
                    )
                    GiselleBus.set(GiselleState.THINKING)
                    val result = claude.decide(context)
                    if (result !is DecisionResult.Success) {
                        circuitBreaker.recordFailure()
                        val reason = (result as DecisionResult.Failure).reason
                        onSpeak(claude.fallbackDecision(reason).userFacingSummary)
                        GiselleBus.set(GiselleState.ERROR); GiselleBus.celebrate(false)
                        return@launch
                    }
                    circuitBreaker.recordSuccess()
                    runCatching { onHealthyCycle?.invoke() }
                    val decision = SafetyGate.apply(result.decision, app)
                    val risk = SafetyGate.evaluate(decision, app)

                    if (decision.requiresConfirmation) {
                        val pending = PendingAction(decision, app)
                        pendingQueue.add(pending)
                        onNeedsConfirmation(pending)
                        logAction(decision, context, ActionOutcome.PENDING_CONFIRMATION, risk)
                        return@launch
                    }

                    val terminal = decision.actionType in TERMINAL_TYPES
                    if (!terminal) GiselleBus.set(GiselleState.EXECUTING)
                    val outcome = if (terminal) ActionOutcome.SUCCESS else execute(decision)
                    logAction(decision, context, outcome, risk)
                    if (decision.userFacingSummary.isNotBlank() && decision.actionType != ActionType.NO_ACTION) {
                        onSpeak(decision.userFacingSummary)
                    }
                    if (terminal) { GiselleBus.celebrate(decision.actionType == ActionType.ANSWER_ONLY); return@launch }

                    consecutiveFails = if (outcome == ActionOutcome.SUCCESS) 0 else consecutiveFails + 1
                    if (consecutiveFails >= failLimit) {
                        onSpeak("Não consegui concluir isso. Tentei duas vezes e falhou.")
                        GiselleBus.set(GiselleState.ERROR); GiselleBus.celebrate(false)
                        return@launch
                    }
                    // Dá tempo da UI/app reagir e re-lê a tela (verificação).
                    delay(900)
                    screenReader?.invoke()?.let { (a, s) -> app = a; screenText = s }
                }
                onSpeak("Parei após $limit passos sem concluir. Quer que eu continue?")
            } catch (e: Exception) {
                circuitBreaker.recordFailure()
                onSpeak("Tive um problema pra processar isso: ${e.message ?: "erro desconhecido"}")
            } finally {
                GiselleBus.taskActive = false
                // Se ainda há fala em fila, o TTS devolve o IDLE no fim; senão voltamos já.
                if (GiselleBus.pendingSpeech.get() == 0) {
                    if (GiselleBus.state.value == GiselleState.ERROR) delay(1500)
                    GiselleBus.set(GiselleState.IDLE)
                }
            }
        }
    }

    companion object {
        fun isLong(c: String) = Regex("enquanto eu durmo|modo longo|tarefa longa", RegexOption.IGNORE_CASE).containsMatchIn(c)

        val TERMINAL_TYPES = setOf(ActionType.ANSWER_ONLY, ActionType.ASK_USER, ActionType.NO_ACTION)
        val NATIVE_TYPES = setOf(
            ActionType.CALL, ActionType.SEND_SMS, ActionType.SET_ALARM,
            ActionType.SET_TIMER, ActionType.OPEN_URL, ActionType.SET_VOLUME, ActionType.SET_ROUTINE,
            ActionType.REPLY_NOTIFICATION, ActionType.ANSWER_CALL, ActionType.REJECT_CALL,
            ActionType.LOG_EXPENSE, ActionType.LEARN_SKILL, ActionType.SET_SKIN,
            ActionType.MOBILE_MONEY, ActionType.STUDY_MODE
        )
    }

    fun resolveConfirmation(pending: PendingAction, approved: Boolean): Job {
        return scope.launch {
            val risk = SafetyGate.evaluate(pending.decision, pending.app)
            if (approved) {
                val outcome = execute(pending.decision)
                logAction(pending.decision, null, outcome, risk, app = pending.app)
            } else {
                logAction(pending.decision, null, ActionOutcome.CANCELLED_BY_USER, risk, app = pending.app)
                memory.remember(
                    content = "Usuário rejeitou a ação: '${pending.decision.userFacingSummary}' " +
                        "no app ${pending.app}. Evitar repetir esse tipo de ação sem perguntar antes.",
                    category = MemoryCategory.CORRECTION
                )
            }
            pendingQueue.remove(pending)
        }
    }

    private suspend fun execute(decision: AgentDecision): ActionOutcome {
        // OPEN_APP não depende do AccessibilityService, então checa primeiro.
        if (decision.actionType == ActionType.OPEN_APP) {
            val name = decision.target ?: decision.inputText
            val success = name?.let { appLauncher.openByName(it) } ?: false
            return if (success) ActionOutcome.SUCCESS else ActionOutcome.FAILED
        }

        when (decision.actionType) {
            ActionType.READ_NOTIFICATIONS -> { onSpeak(NotificationInbox.digest()); return ActionOutcome.SUCCESS }
            ActionType.WEB_SEARCH -> {
                val query = (decision.target ?: decision.inputText)?.trim().orEmpty()
                val port = webSearchPort ?: return ActionOutcome.FAILED
                if (query.isBlank()) return ActionOutcome.FAILED
                val answer = port.search(query) ?: return ActionOutcome.FAILED
                onSpeak(answer); return ActionOutcome.SUCCESS
            }
            ActionType.DAILY_BRIEF -> {
                val c = appContext ?: return ActionOutcome.FAILED
                onSpeak(DailyBrief.build(c)); return ActionOutcome.SUCCESS
            }
            ActionType.EXPENSE_REPORT -> {
                val c = appContext ?: return ActionOutcome.FAILED
                onSpeak(Ledger.report(c, decision.target ?: "semana")); return ActionOutcome.SUCCESS
            }
            ActionType.DESCRIBE_SCREEN -> {
                val a = visionPort?.describe(decision.target ?: decision.inputText ?: "") ?: return ActionOutcome.FAILED
                onSpeak(a); return ActionOutcome.SUCCESS
            }
            ActionType.RUN_SKILL -> {
                val c = appContext ?: return ActionOutcome.FAILED
                val sk = SkillStore.get(c, decision.target ?: "") ?: return ActionOutcome.FAILED
                return if (skillRunner.run(sk, decision.inputText ?: "")) ActionOutcome.SUCCESS else ActionOutcome.FAILED
            }
            ActionType.REMEMBER -> {
                val txt = decision.inputText?.trim().orEmpty()
                if (txt.isBlank()) return ActionOutcome.FAILED
                val kind = decision.target?.lowercase().orEmpty()
                val cat = when (kind) { "rotina" -> MemoryCategory.ROUTINE; "preferencia", "preferência" -> MemoryCategory.PREFERENCE; else -> MemoryCategory.FACT }
                memory.remember(if (kind == "pessoa") "Pessoa: $txt" else txt, cat)
                return ActionOutcome.SUCCESS
            }
            else -> {}
        }

        if (decision.actionType == ActionType.APP_MESSAGE) {
            val parts = (decision.target ?: "").split("|", limit = 2)
            val ok = screen.isAvailable() &&
                messenger.send(parts[0].trim(), parts.getOrElse(1) { "" }.trim(), decision.inputText ?: "")
            return if (ok) ActionOutcome.SUCCESS else ActionOutcome.FAILED
        }

        if (decision.actionType in NATIVE_TYPES) {
            val ok = intents?.execute(decision) ?: false
            return if (ok) ActionOutcome.SUCCESS else ActionOutcome.FAILED
        }

        if (!screen.isAvailable()) return ActionOutcome.FAILED

        val success = when (decision.actionType) {
            ActionType.CLICK -> decision.target?.let { t ->
                screen.click(t) || (visionPort?.locate(t)?.let { screen.tapAt(it.x, it.y) } ?: false)
            } ?: false
            ActionType.TYPE_TEXT -> decision.inputText?.let { screen.typeText(it, decision.target) } ?: false
            ActionType.GO_BACK -> screen.goBack()
            ActionType.GO_HOME -> screen.goHome()
            ActionType.SWIPE -> {
                val from = decision.coordinates
                val to = decision.endCoordinates
                if (from != null && to != null) screen.swipe(from.x, from.y, to.x, to.y)
                else false
            }
            ActionType.WAIT, ActionType.ASK_USER, ActionType.ANSWER_ONLY, ActionType.NO_ACTION -> true
            ActionType.OPEN_APP -> true // já tratado acima, nunca cai aqui
            ActionType.CALL, ActionType.SEND_SMS, ActionType.SET_ALARM,
            ActionType.SET_TIMER, ActionType.OPEN_URL, ActionType.SET_VOLUME,
            ActionType.SET_ROUTINE, ActionType.APP_MESSAGE, ActionType.READ_NOTIFICATIONS,
            ActionType.REPLY_NOTIFICATION, ActionType.ANSWER_CALL, ActionType.REJECT_CALL, ActionType.DAILY_BRIEF,
            ActionType.LOG_EXPENSE, ActionType.EXPENSE_REPORT, ActionType.DESCRIBE_SCREEN, ActionType.REMEMBER,
            ActionType.RUN_SKILL, ActionType.LEARN_SKILL, ActionType.SET_SKIN,
            ActionType.MOBILE_MONEY, ActionType.STUDY_MODE, ActionType.WEB_SEARCH -> true // tratados acima
        }

        return if (success) ActionOutcome.SUCCESS else ActionOutcome.FAILED
    }

    private suspend fun logAction(
        decision: AgentDecision,
        context: PerceptionContext?,
        outcome: ActionOutcome,
        risk: RiskLevel,
        app: String? = null
    ) {
        val appName = context?.currentApp ?: app ?: "desconhecido"

        val entry = ActionLogEntry(
            timestamp = System.currentTimeMillis(),
            app = appName,
            command = context?.userCommand,
            decision = decision,
            outcome = outcome
        )
        if (shortTermHistory.size >= maxHistorySize) shortTermHistory.removeFirst()
        shortTermHistory.addLast(entry)

        // Item 8: todo desfecho — sucesso, falha, confirmação, rejeição —
        // vira uma linha imutável no log auditável, independente do
        // que acontece com a memória de aprendizado.
        auditLog.record(
            app = appName,
            userCommand = context?.userCommand,
            decision = decision,
            riskLevel = risk,
            outcome = outcome
        )

        // Padrões bem-sucedidos e repetidos viram memória de longo prazo.
        // Isso é o mecanismo real de "evolução" do sistema.
        if (outcome == ActionOutcome.SUCCESS && decision.actionType != ActionType.ANSWER_ONLY) {
            memory.remember(
                content = "Ação bem-sucedida: '${decision.userFacingSummary}' no app $appName.",
                category = MemoryCategory.APP_BEHAVIOR
            )
        }
    }

    private fun <T> ArrayDeque<T>.addLast(item: T, maxSize: Int = 20) {
        if (size >= maxSize) removeFirst()
        add(item)
    }
}

data class PendingAction(
    val decision: AgentDecision,
    val app: String,
    val createdAt: Long = System.currentTimeMillis()
)
