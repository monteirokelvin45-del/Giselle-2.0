// ============================================================
// tracker/agent/TrackerOrchestrator.kt
// ------------------------------------------------------------
// Equivalente ao AgentOrchestrator do núcleo, mas para o modo
// "tracker": em vez de agir na tela, ele OBSERVA os registros
// que o usuário já confirmou e gera sugestões (AiEvent).
//
// PRINCÍPIO HERDADO DO SAFETYGATE: este orquestrador NUNCA
// escreve em tabelas de dados do usuário (registro_fisico,
// registro_financeiro etc). Ele só lê e escreve em ai_events —
// a mesma separação de "plano vs. registro confirmado" do
// TrackerModels.kt se aplica aqui como "sugestão vs. dado real".
// Se o usuário aceitar uma sugestão que implica editar um dado,
// isso passa pelo AuditedTrackerRepository, nunca por aqui direto.
// ============================================================

package com.example.jarvis.tracker.agent

import com.example.jarvis.core.CircuitBreaker
import com.example.jarvis.core.RateLimiter
import com.example.jarvis.llm.InsightDecisionResult
import com.example.jarvis.llm.TrackerInsightClient
import com.example.jarvis.tracker.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class TrackerOrchestrator(
    private val claude: TrackerInsightClient,
    private val configPerfilDao: ConfigPerfilDao,
    private val metasDao: MetasDao,
    private val registroFisicoDao: RegistroFisicoDao,
    private val registroMentalDao: RegistroMentalDao,
    private val registroEstudoDao: RegistroEstudoDao,
    private val registroFinanceiroDao: RegistroFinanceiroDao,
    private val aiEventDao: AiEventDao,
    private val rateLimiter: RateLimiter,
    private val circuitBreaker: CircuitBreaker,
    private val onInsightGenerated: (AiEvent) -> Unit,
    private val onCircuitOpen: (String) -> Unit,
    private val onInsufficientData: (String) -> Unit = {}
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Roda a análise: monta o TrackerContext a partir do banco, pede ao
     * Claude um diagnóstico, e se vier algo relevante, grava como AiEvent
     * (nunca como alteração direta de dado). Chamado por um trigger
     * explícito (botão "gerar diagnóstico") ou por um agendamento
     * periódico (ex: 1x por dia) — nunca a cada tela aberta, pra não
     * gastar API à toa.
     */
    fun generateInsight(origem: String = "tracker") {
        scope.launch { runInsight(origem) }
    }

    /** Worker usa o resultado real para decidir entre success e retry. */
    suspend fun generateInsightAndAwait(origem: String = "workmanager_diario"): Boolean =
        runInsight(origem)

    private suspend fun runInsight(origem: String): Boolean {
        if (!circuitBreaker.allowRequest()) {
            onCircuitOpen("análise pausada por falhas recentes, tentando novamente em instantes")
            return false
        }
        if (!rateLimiter.tryAcquire()) return false
        return try {
            val sample = buildDataSample()
            val preCheck = TrackerSafetyGate.evaluateBeforeCall(sample)
            if (preCheck is TrackerSafetyVerdict.Blocked) {
                onInsufficientData(preCheck.reason)
                return true
            }
            when (val result = claude.generateInsight(buildContext())) {
                is InsightDecisionResult.Success -> {
                    circuitBreaker.recordSuccess()
                    val insight = result.insight
                    if (insight != null) {
                        val postCheck = TrackerSafetyGate.evaluateInsight(insight, sample)
                        if (postCheck is TrackerSafetyVerdict.Blocked) {
                            onInsufficientData(postCheck.reason)
                            return true
                        }
                        val event = AiEvent(
                            origem = origem,
                            tipo = insight.tipo.name,
                            dadosObservadosJson = insight.dadosObservados,
                            sugestaoTexto = insight.sugestaoTexto,
                            feedback = null,
                            createdAt = System.currentTimeMillis()
                        )
                        val id = aiEventDao.insert(event)
                        onInsightGenerated(event.copy(id = id))
                    }
                    true // insight == null é um resultado válido.
                }
                is InsightDecisionResult.Failure -> {
                    circuitBreaker.recordFailure()
                    false
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            circuitBreaker.recordFailure()
            false
        }
    }

    /**
     * Chamado pela UI quando o usuário reage a uma sugestão.
     * Isso é o ÚNICO lugar onde feedback é escrito — reforça a regra
     * do TrackerModels.kt de que aprendizado nasce de reação explícita.
     */
    fun resolveInsightFeedback(eventId: Long, feedback: TrackerFeedback) {
        scope.launch {
            aiEventDao.registrarFeedback(eventId, feedback.name.lowercase())
        }
    }

    private suspend fun buildDataSample(): TrackerDataSample {
        val now = System.currentTimeMillis()
        val seteDiasAtras = now - TimeUnit.DAYS.toMillis(7)
        return TrackerDataSample(
            diasComRegistroFisico = registroFisicoDao.countDiasComRegistro(seteDiasAtras, now),
            diasComRegistroMental = registroMentalDao.countDiasComRegistro(seteDiasAtras, now),
            diasComRegistroEstudo = registroEstudoDao.countDiasComRegistro(seteDiasAtras, now),
            diasComRegistroFinanceiro = registroFinanceiroDao.countDiasComRegistro(seteDiasAtras, now)
        )
    }

    private suspend fun buildContext(): TrackerContext {
        val perfil = configPerfilDao.get()
        val metas = metasDao.get()
        val now = System.currentTimeMillis()
        val seteDiasAtras = now - TimeUnit.DAYS.toMillis(7)

        val fisico = registroFisicoDao.getByRange(seteDiasAtras, now)
        val mental = registroMentalDao.getByRange(seteDiasAtras, now)
        val estudoMinutos = registroEstudoDao.somaMinutosNaJanela(seteDiasAtras, now)
        val receitas = registroFinanceiroDao.somaReceitas(seteDiasAtras, now)
        val despesas = registroFinanceiroDao.somaDespesas(seteDiasAtras, now)
        val semFeedback = aiEventDao.getSemFeedback()

        val perfilResumo = perfil?.let {
            "objetivo: ${it.objetivoPrincipal ?: "não definido"}, nível: ${it.nivelExperienciaTreino ?: "?"}"
        } ?: "perfil ainda não confirmado"

        val metasResumo = metas?.let {
            "peso alvo: ${it.pesoIdealKg ?: "?"}kg, estudo/semana: ${it.horasEstudoSemanaAlvo ?: "?"}h"
        } ?: "metas ainda não definidas"

        val janela = buildString {
            append("Treinos confirmados (7 dias): ${fisico.count { it.treinoConfirmado }}\n")
            append("Registros de sono/mental (7 dias): ${mental.size}\n")
            append("Minutos de estudo (7 dias): $estudoMinutos\n")
            append("Receitas (7 dias): ${receitas / 100.0} | Despesas (7 dias): ${despesas / 100.0}\n")
        }

        return TrackerContext(
            perfilResumo = perfilResumo,
            metasResumo = metasResumo,
            janelaRecente = janela,
            eventosSemFeedback = semFeedback.map { it.sugestaoTexto }
        )
    }
}

enum class TrackerFeedback { ACEITA, RECUSADA, IGNORADA }

// ============================================================
// NOTA DE CONCORRÊNCIA (dívida técnica reconhecida, não resolvida):
// generateInsight() não usa @Transaction nem Mutex entre a leitura
// de contagens (buildDataSample/buildContext) e a escrita do
// AiEvent resultante. Em uso sequencial single-user isso é seguro;
// o risco aparece quando duas chamadas concorrentes (ex: voz +
// WorkManager futuro) puderem correr ao mesmo tempo. Se/quando
// WorkManager for implementado, envolver o trecho crítico com um
// Mutex por domínio antes de liberar execução paralela real.
// ============================================================
