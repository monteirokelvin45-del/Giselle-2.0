package com.example.jarvis.business

import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalGateTest {
    private val now = 1_000_000_000_000L
    private val store = FakeBizStore()
    private val gate = ApprovalGate(store, clock = { now })

    @Test fun `no maximo 10 pendencias`() {
        repeat(ApprovalGate.MAX_PENDING) { assertTrue(gate.request(Kind.PROPOSAL, "p$it") is ApprovalGate.Outcome.Ok) }
        assertTrue(gate.request(Kind.PROPOSAL, "extra") is ApprovalGate.Outcome.Rejected)
    }

    @Test fun `aprovar duas vezes conta uma vez`() {
        val id = (gate.request(Kind.SPEND, "domínio", 10_00) as ApprovalGate.Outcome.Ok).item.id
        assertTrue(gate.approve(id) is ApprovalGate.Outcome.Ok)
        assertTrue(gate.approve(id) is ApprovalGate.Outcome.Rejected)
    }

    @Test fun `negar impede aprovar depois`() {
        val id = (gate.request(Kind.PROPOSAL, "x") as ApprovalGate.Outcome.Ok).item.id
        assertTrue(gate.deny(id) is ApprovalGate.Outcome.Ok)
        assertTrue(gate.approve(id) is ApprovalGate.Outcome.Rejected)
    }

    @Test fun `teto por transacao bloqueia o pedido`() {
        assertTrue(gate.request(Kind.SPEND, "caro", 30_00) is ApprovalGate.Outcome.Rejected)
    }

    @Test fun `tetos sao verificados de novo na aprovacao`() {
        val a = (gate.request(Kind.SPEND, "a", 20_00) as ApprovalGate.Outcome.Ok).item.id
        val b = (gate.request(Kind.SPEND, "b", 20_00) as ApprovalGate.Outcome.Ok).item.id
        val c = (gate.request(Kind.SPEND, "c", 20_00) as ApprovalGate.Outcome.Ok).item.id
        // a fachada grava SPEND_APPROVED depois de cada aprovação de gasto
        assertTrue(gate.approve(a) is ApprovalGate.Outcome.Ok)
        store.addLedger(Ledger.SPEND_APPROVED, 20_00, "a", now)
        assertTrue(gate.approve(b) is ApprovalGate.Outcome.Ok)
        store.addLedger(Ledger.SPEND_APPROVED, 20_00, "b", now)
        // 20 + 20 + 20 = 60 > 50 diários
        assertTrue(gate.approve(c) is ApprovalGate.Outcome.Rejected)
    }
}
