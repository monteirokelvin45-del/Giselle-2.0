// ============================================================
// test/tracker/agent/TrackerSafetyGateTest.kt
// ------------------------------------------------------------
// Cobre a pré-checagem (sem dado nenhum -> bloqueia antes de
// chamar a API) e a pós-checagem (insight sobre domínio com
// pouco dado -> descartado mesmo se outro domínio tinha mais).
// ============================================================

package com.example.jarvis.tracker.agent

import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerSafetyGateTest {

    private fun insight(tipo: TrackerInsightType) = TrackerInsight(
        tipo = tipo,
        dadosObservados = "teste",
        sugestaoTexto = "sugestão de teste",
        prioridade = TrackerInsightPrioridade.MEDIA
    )

    // -----------------------------------------------------
    // Pré-checagem
    // -----------------------------------------------------
    @Test
    fun `bloqueia quando nenhum dominio tem dado suficiente`() {
        val sample = TrackerDataSample(
            diasComRegistroFisico = 1, diasComRegistroMental = 0,
            diasComRegistroEstudo = 2, diasComRegistroFinanceiro = 0
        )
        val verdict = TrackerSafetyGate.evaluateBeforeCall(sample)
        assertTrue(verdict is TrackerSafetyVerdict.Blocked)
    }

    @Test
    fun `permite quando pelo menos um dominio atinge o minimo`() {
        val sample = TrackerDataSample(
            diasComRegistroFisico = 3, diasComRegistroMental = 0,
            diasComRegistroEstudo = 0, diasComRegistroFinanceiro = 0
        )
        val verdict = TrackerSafetyGate.evaluateBeforeCall(sample)
        assertTrue(verdict is TrackerSafetyVerdict.Allowed)
    }

    @Test
    fun `zero registros em todos os dominios bloqueia`() {
        val sample = TrackerDataSample(0, 0, 0, 0)
        val verdict = TrackerSafetyGate.evaluateBeforeCall(sample)
        assertTrue(verdict is TrackerSafetyVerdict.Blocked)
    }

    // -----------------------------------------------------
    // Pós-checagem
    // -----------------------------------------------------
    @Test
    fun `bloqueia insight de financas quando o dominio financeiro tem pouco dado`() {
        // físico tem dado suficiente (passou na pré-checagem), mas o
        // insight que voltou foi especificamente sobre finanças, que não tem
        val sample = TrackerDataSample(
            diasComRegistroFisico = 5, diasComRegistroMental = 0,
            diasComRegistroEstudo = 0, diasComRegistroFinanceiro = 1
        )
        val verdict = TrackerSafetyGate.evaluateInsight(insight(TrackerInsightType.INSIGHT_FINANCAS), sample)
        assertTrue(verdict is TrackerSafetyVerdict.Blocked)
    }

    @Test
    fun `permite insight de fisico quando o dominio fisico tem dado suficiente`() {
        val sample = TrackerDataSample(
            diasComRegistroFisico = 4, diasComRegistroMental = 0,
            diasComRegistroEstudo = 0, diasComRegistroFinanceiro = 0
        )
        val verdict = TrackerSafetyGate.evaluateInsight(insight(TrackerInsightType.INSIGHT_TREINO), sample)
        assertTrue(verdict is TrackerSafetyVerdict.Allowed)
    }

    @Test
    fun `diagnostico geral nao e bloqueado por dominio especifico`() {
        val sample = TrackerDataSample(
            diasComRegistroFisico = 3, diasComRegistroMental = 0,
            diasComRegistroEstudo = 0, diasComRegistroFinanceiro = 0
        )
        val verdict = TrackerSafetyGate.evaluateInsight(insight(TrackerInsightType.DIAGNOSTICO_GERAL), sample)
        assertTrue(verdict is TrackerSafetyVerdict.Allowed)
    }

    @Test
    fun `insight de consistencia depende do dominio mental`() {
        val sample = TrackerDataSample(
            diasComRegistroFisico = 10, diasComRegistroMental = 1,
            diasComRegistroEstudo = 10, diasComRegistroFinanceiro = 10
        )
        val verdict = TrackerSafetyGate.evaluateInsight(insight(TrackerInsightType.INSIGHT_CONSISTENCIA), sample)
        assertTrue(verdict is TrackerSafetyVerdict.Blocked)
    }
}
