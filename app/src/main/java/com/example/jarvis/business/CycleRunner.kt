// business/CycleRunner.kt — um ciclo do motor. Cada passo roda isolado: uma falha não derruba os outros.
package com.example.jarvis.business

import kotlinx.coroutines.CancellationException

class CycleRunner(
    private val store: BizStore,
    private val guard: BrainGuard,
    private val gate: ApprovalGate,
    private val hunter: FreelanceHunter,
    private val balances: Balances,
    private val clock: () -> Long,
    private val emit: (String) -> Unit,
) {
    private val reflectionIntervalMs = ApprovalGate.DAY_MS

    /** Devolve os nomes dos passos que falharam (para o registo). */
    suspend fun cycle(tier: Tier): List<String> {
        val failed = mutableListOf<String>()
        suspend fun step(name: String, block: suspend () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += name
                emit("Um passo falhou ($name). Os outros continuam.")
            }
        }

        step("transicao") { checkTransition() }
        step("conciliacao") { checkPayout() }
        if (tier.useAi) {
            step("trabalho") { work() }
            step("reflexao") { reflect() }
        }
        store.setKv(BizKeys.LAST_RUN, clock().toString())
        return failed
    }

    fun phase(): Phase = Phase.fromNumber(store.getKv(BizKeys.PHASE)?.toIntOrNull() ?: 1)

    private fun checkTransition() {
        val current = phase()
        val goal = Cents(store.getKv(BizKeys.GOAL)?.toLongOrNull() ?: DEFAULT_GOAL_CENTS)
        val next = PhaseManager.afterVaultCheck(current, balances.vault(), goal)
        if (next != current) {
            store.setKv(BizKeys.PHASE, next.number.toString())
            emit("Meta do cofre atingida. Passei para a fase ${next.number}.")
        }
    }

    private fun checkPayout() {
        val owner = balances.ownerBalance()
        if (!PhaseManager.payoutDue(owner)) return
        if (store.pendingApprovals().any { it.kind == Kind.PAYOUT }) return
        val text = "Conciliação: ${owner.format()} acumulados na tua parte. " +
            "Aprova depois de registares o recebimento na tua conta."
        if (gate.request(Kind.PAYOUT, text, owner.value) is ApprovalGate.Outcome.Ok) {
            emit("Tens ${owner.format()} para conciliar. Vê as pendências.")
        }
    }

    private suspend fun work() {
        when (phase()) {
            Phase.FOUNDATION -> {
                val ids = hunter.run()
                if (ids.isNotEmpty()) emit("Propostas prontas: " + ids.joinToString(", ") { "#$it" } + ".")
            }
            Phase.BUSINESS -> businessWork()
            Phase.SCALE -> Unit // fase 3 é decidida e executada por ti
        }
    }

    private suspend fun businessWork() {
        if (store.getKv(BizKeys.PLAN_APPROVED) != "1") {
            if (store.pendingApprovals().any { it.kind == Kind.PLAN }) return
            val plan = BusinessModule.plan { s, u, m -> guard.ask(s, u, m) } ?: return
            if (gate.request(Kind.PLAN, plan, 0L) is ApprovalGate.Outcome.Ok) emit("Plano pronto para aprovação.")
            return
        }
        val today = (clock() / ApprovalGate.DAY_MS).toString()
        if (store.getKv(BizKeys.LAST_POSTS_DAY) == today) return
        val plan = store.approval(store.getKv("approved_plan_id")?.toLongOrNull() ?: return)?.text ?: return
        val posts = BusinessModule.posts({ s, u, m -> guard.ask(s, u, m) }, plan) ?: return
        if (gate.request(Kind.POST, posts, 0L) is ApprovalGate.Outcome.Ok) {
            store.setKv(BizKeys.LAST_POSTS_DAY, today)
            emit("Lote de posts pronto para aprovação.")
        }
    }

    private suspend fun reflect() {
        val last = store.getKv(BizKeys.LAST_REFLECTION)?.toLongOrNull() ?: 0L
        if (clock() - last < reflectionIntervalMs) return
        val summary = "Fase ${phase().number}, cofre ${balances.vault().format()}, " +
            "pendências ${store.pendingCount()}."
        val text = guard.ask(
            "Escreves até 60 palavras, em português, sobre o próximo passo do negócio. Usa só os dados fornecidos.",
            summary,
            90,
        ) ?: return
        store.setKv(BizKeys.REFLECTION, clampWords(text, 60))
        store.setKv(BizKeys.LAST_REFLECTION, clock().toString())
    }

    companion object {
        /** Meta por omissão (USD 300). Configurável com "meta X". */
        const val DEFAULT_GOAL_CENTS = 300_00L
    }
}
