// ============================================================
// test/tracker/venda/VendaSafetyGateTest.kt
// ============================================================

package com.example.jarvis.tracker.venda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VendaSafetyGateTest {

    private fun action(tipo: VendaActionType, valor: Double? = null) = VendaAction(
        tipo = tipo, plataforma = "hotmart", valorEnvolvido = valor, descricao = "teste"
    )

    @Test
    fun `consultar metricas e sempre seguro`() {
        assertEquals(VendaRiskLevel.Safe, VendaSafetyGate.evaluate(action(VendaActionType.CONSULTAR_METRICAS)))
        assertTrue(VendaSafetyGate.canProceedAutonomously(action(VendaActionType.CONSULTAR_METRICAS)))
    }

    @Test
    fun `responder cliente e sensivel mas nao bloqueado duro`() {
        val result = VendaSafetyGate.evaluate(action(VendaActionType.RESPONDER_MENSAGEM_CLIENTE))
        assertEquals(VendaRiskLevel.Sensitive, result)
        assertFalse(VendaSafetyGate.canProceedAutonomously(action(VendaActionType.RESPONDER_MENSAGEM_CLIENTE)))
    }

    @Test
    fun `alterar preco e sempre bloqueado`() {
        assertEquals(VendaRiskLevel.Blocked, VendaSafetyGate.evaluate(action(VendaActionType.ALTERAR_PRECO, valor = 99.0)))
    }

    @Test
    fun `emitir reembolso e sempre bloqueado independente do valor`() {
        assertEquals(VendaRiskLevel.Blocked, VendaSafetyGate.evaluate(action(VendaActionType.EMITIR_REEMBOLSO, valor = 1.0)))
        assertEquals(VendaRiskLevel.Blocked, VendaSafetyGate.evaluate(action(VendaActionType.EMITIR_REEMBOLSO, valor = 10000.0)))
    }

    @Test
    fun `publicar e despublicar produto sao sempre bloqueados`() {
        assertEquals(VendaRiskLevel.Blocked, VendaSafetyGate.evaluate(action(VendaActionType.PUBLICAR_PRODUTO)))
        assertEquals(VendaRiskLevel.Blocked, VendaSafetyGate.evaluate(action(VendaActionType.DESPUBLICAR_PRODUTO)))
    }

    @Test
    fun `nenhuma acao bloqueada pode prosseguir autonomamente`() {
        val bloqueadas = listOf(
            VendaActionType.ALTERAR_PRECO, VendaActionType.PUBLICAR_PRODUTO,
            VendaActionType.DESPUBLICAR_PRODUTO, VendaActionType.EMITIR_REEMBOLSO,
            VendaActionType.ENVIAR_PROPOSTA
        )
        bloqueadas.forEach {
            assertFalse("$it não deveria prosseguir sozinho", VendaSafetyGate.canProceedAutonomously(action(it)))
        }
    }
}
