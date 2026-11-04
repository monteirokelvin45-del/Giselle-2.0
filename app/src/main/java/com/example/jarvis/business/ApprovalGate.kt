// business/ApprovalGate.kt — porteiro das pendências: máximo 10, tetos verificados de novo na aprovação,
// e aprovação idempotente (um toque conta uma vez).
package com.example.jarvis.business

class ApprovalGate(
    private val store: BizStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed class Outcome {
        data class Ok(val item: ApprovalItem) : Outcome()
        data class Rejected(val reason: String) : Outcome()
    }

    fun request(kind: String, text: String, cents: Long = 0L): Outcome {
        if (store.pendingCount() >= MAX_PENDING) {
            return Outcome.Rejected("já há $MAX_PENDING pendências; resolve algumas antes")
        }
        if (kind == Kind.SPEND) capReason(cents)?.let { return Outcome.Rejected(it) }
        val id = store.addApproval(kind, text, cents, clock())
        val item = store.approval(id) ?: return Outcome.Rejected("falha ao gravar o pedido")
        return Outcome.Ok(item)
    }

    /** Os tetos são verificados de novo aqui, no momento de aprovar. */
    fun approve(id: Long): Outcome = resolve(id, approve = true)

    fun deny(id: Long): Outcome = resolve(id, approve = false)

    private fun resolve(id: Long, approve: Boolean): Outcome {
        val item = store.approval(id) ?: return Outcome.Rejected("o pedido #$id não existe")
        if (item.status != Status.PENDING) return Outcome.Rejected("o pedido #$id já está ${item.status.lowercase()}")
        if (approve && item.kind == Kind.SPEND) capReason(item.cents)?.let { return Outcome.Rejected(it) }
        if (!store.resolveApproval(id, approve)) return Outcome.Rejected("o pedido #$id já foi resolvido")
        return Outcome.Ok(item.copy(status = if (approve) Status.APPROVED else Status.DENIED))
    }

    private fun capReason(cents: Long): String? {
        val now = clock()
        val today = store.sumLedger(Ledger.SPEND_APPROVED, now - DAY_MS, now)
        val month = store.sumLedger(Ledger.SPEND_APPROVED, now - 30 * DAY_MS, now)
        return SpendPolicy.checkPlanned(Cents(cents), Cents(today), Cents(month))
    }

    companion object {
        const val MAX_PENDING = 10
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
