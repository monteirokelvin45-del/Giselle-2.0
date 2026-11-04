// business/PhaseManager.kt — transições automáticas de fase e gatilho de conciliação.
package com.example.jarvis.business

object PhaseManager {
    /** Da fase 1 para a 2 só quando o cofre atinge a meta. A fase 3 só é ativada por comando ("empresa pronta"). */
    fun afterVaultCheck(current: Phase, vault: Cents, goal: Cents): Phase =
        if (current == Phase.FOUNDATION && vault.value >= goal.value) Phase.BUSINESS else current

    fun payoutDue(ownerBalance: Cents, threshold: Cents = Cents(25_00)): Boolean =
        ownerBalance.value > threshold.value
}
