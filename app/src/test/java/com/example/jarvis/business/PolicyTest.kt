package com.example.jarvis.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {
    @Test fun `fase 3 divide 30-30-40 e o resto em centavos fica com o utilizador`() {
        val s = Treasury.split(Cents(1001), Phase.SCALE) // USD 10.01
        assertEquals(0L, s.foundation.value)
        assertEquals(300L, s.reinvest.value)
        assertEquals(300L, s.idea.value)
        assertEquals(401L, s.owner.value) // 40% mais o arredondamento
        assertEquals(1001L, s.reinvest.value + s.idea.value + s.owner.value)
    }

    @Test fun `fases 1 e 2 mandam tudo para a fundacao`() {
        assertEquals(Cents(500), Treasury.split(Cents(500), Phase.FOUNDATION).foundation)
        assertEquals(Cents(500), Treasury.split(Cents(500), Phase.BUSINESS).foundation)
    }

    @Test fun `cofre e fundacao mais reinvestimento menos gastos reais`() {
        val store = FakeBizStore()
        store.addLedger(Ledger.FOUNDATION, 100_00, "", 1)
        store.addLedger(Ledger.REINVEST, 30_00, "", 1)
        store.addLedger(Ledger.SPEND_REAL, 20_00, "", 1)
        store.addLedger(Ledger.SPEND_APPROVED, 999_00, "", 1) // aprovado não é gasto real
        assertEquals(110_00L, Balances(store).vault().value)
    }

    @Test fun `nivel por bateria e saude do modelo`() {
        assertEquals(Tier.CRITICAL, TierPolicy.of(10, 0))
        assertEquals(Tier.LOW, TierPolicy.of(30, 0))
        assertEquals(Tier.LOW, TierPolicy.of(90, TierPolicy.MODEL_ERRORS_BEFORE_DEGRADED))
        assertEquals(Tier.NORMAL, TierPolicy.of(90, 0))
        assertFalse(Tier.CRITICAL.useAi)
        assertEquals(6L, Tier.CRITICAL.intervalHours)
    }

    @Test fun `transicao so da fase 1 para 2 ao atingir a meta, e fase 3 nunca automatica`() {
        assertEquals(Phase.BUSINESS, PhaseManager.afterVaultCheck(Phase.FOUNDATION, Cents(300_00), Cents(300_00)))
        assertEquals(Phase.FOUNDATION, PhaseManager.afterVaultCheck(Phase.FOUNDATION, Cents(299_99), Cents(300_00)))
        assertEquals(Phase.SCALE, PhaseManager.afterVaultCheck(Phase.SCALE, Cents(999_00), Cents(300_00)))
    }

    @Test fun `conciliacao so acima de 25 USD`() {
        assertFalse(PhaseManager.payoutDue(Cents(25_00)))
        assertTrue(PhaseManager.payoutDue(Cents(25_01)))
    }

    @Test fun `teto de gasto planeado`() {
        assertNull(SpendPolicy.checkPlanned(Cents(10_00), Cents(0), Cents(0)))
        assertNotNull(SpendPolicy.checkPlanned(Cents(25_01), Cents(0), Cents(0)))
        assertNotNull(SpendPolicy.checkPlanned(Cents(20_00), Cents(40_00), Cents(40_00)))
        assertNotNull(SpendPolicy.checkPlanned(Cents(20_00), Cents(0), Cents(190_00)))
        assertNotNull(SpendPolicy.checkPlanned(Cents(0), Cents(0), Cents(0)))
    }

    @Test fun `a constituicao bloqueia envio, dinheiro, contas e permissoes`() {
        assertEquals(Verdict.ALLOW, Constitution.judge(ActionKind.DRAFT))
        assertEquals(Verdict.ALLOW, Constitution.judge(ActionKind.REQUEST_APPROVAL))
        ActionKind.values().filter { it != ActionKind.DRAFT && it != ActionKind.REQUEST_APPROVAL }
            .forEach { assertEquals(it.name, Verdict.BLOCKED, Constitution.judge(it)) }
        assertEquals(3, Constitution.LAWS.size)
    }
}
