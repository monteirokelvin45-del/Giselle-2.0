// business/SpendPolicy.kt — tetos para gastos PLANEADOS (pedidos de aprovação).
// Registar um gasto que já aconteceu ("gastei") não passa por aqui: o app não paga nada.
package com.example.jarvis.business

data class SpendLimits(
    val perTx: Cents = Cents(25_00),
    val perDay: Cents = Cents(50_00),
    val perMonth: Cents = Cents(200_00),
)

object SpendPolicy {
    /** null = dentro do teto; senão, o motivo em português. */
    fun checkPlanned(
        amount: Cents,
        approvedToday: Cents,
        approvedThisMonth: Cents,
        limits: SpendLimits = SpendLimits(),
    ): String? = when {
        amount.value <= 0 -> "valor inválido"
        amount.value > limits.perTx.value -> "acima do teto por transação (${limits.perTx.format()})"
        approvedToday.value + amount.value > limits.perDay.value -> "acima do teto diário (${limits.perDay.format()})"
        approvedThisMonth.value + amount.value > limits.perMonth.value -> "acima do teto mensal (${limits.perMonth.format()})"
        else -> null
    }
}
