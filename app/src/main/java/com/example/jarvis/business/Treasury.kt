// business/Treasury.kt — divisão do dinheiro novo e cálculo do cofre (sempre a partir do razão).
package com.example.jarvis.business

enum class Phase(val number: Int) {
    FOUNDATION(1), BUSINESS(2), SCALE(3);

    companion object {
        fun fromNumber(n: Int): Phase = values().firstOrNull { it.number == n } ?: FOUNDATION
    }
}

data class Split(val foundation: Cents, val reinvest: Cents, val idea: Cents, val owner: Cents)

object Treasury {
    private val ZERO = Cents(0)

    /**
     * Líquido = bruto − taxa (feito antes, em quem chama).
     * Fases 1 e 2: 100% para a fundação. Fase 3: 30% reinvestimento, 30% "ideia milionária",
     * e o resto (40% mais os centavos de arredondamento) fica contigo.
     */
    fun split(net: Cents, phase: Phase): Split = when (phase) {
        Phase.FOUNDATION, Phase.BUSINESS -> Split(net, ZERO, ZERO, ZERO)
        Phase.SCALE -> {
            val reinvest = Cents(net.value * 30 / 100)
            val idea = Cents(net.value * 30 / 100)
            Split(ZERO, reinvest, idea, net - reinvest - idea)
        }
    }

    /** Cofre = fundação + reinvestimento − gastos reais. */
    fun vault(foundation: Cents, reinvest: Cents, spent: Cents): Cents = foundation + reinvest - spent
}

/** Lê os saldos a partir do razão. Nunca são digitados à mão. */
class Balances(private val store: BizStore) {
    fun foundation(): Cents = Cents(store.sumLedger(Ledger.FOUNDATION, 0, Long.MAX_VALUE))
    fun reinvest(): Cents = Cents(store.sumLedger(Ledger.REINVEST, 0, Long.MAX_VALUE))
    fun spentReal(): Cents = Cents(store.sumLedger(Ledger.SPEND_REAL, 0, Long.MAX_VALUE))
    fun vault(): Cents = Treasury.vault(foundation(), reinvest(), spentReal())

    /** Parte tua acumulada menos o que já foi conciliado. */
    fun ownerBalance(): Cents =
        Cents(store.sumLedger(Ledger.OWNER_SHARE, 0, Long.MAX_VALUE) - store.sumLedger(Ledger.OWNER_RECONCILED, 0, Long.MAX_VALUE))
}
