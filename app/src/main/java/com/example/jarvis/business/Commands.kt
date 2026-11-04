// business/Commands.kt — interpreta os comandos de texto do utilizador (pura, testável).
package com.example.jarvis.business

sealed class Command {
    object Status : Command()
    object Help : Command()
    data class Decide(val id: Long, val approve: Boolean) : Command()
    data class Received(val gross: Cents, val fee: Cents) : Command()
    data class SpentReal(val amount: Cents, val note: String) : Command()
    data class PlanSpend(val amount: Cents, val note: String) : Command()
    object CompanyReady : Command()
    object PlanApproved : Command()
    data class Goal(val cents: Cents) : Command()
    data class Skills(val list: List<String>) : Command()
    data class Invalid(val reason: String) : Command()
}

object CommandParser {
    private val DECIDE = Regex("^(aprovar|negar)\\s+#?(\\d{1,9})$", RegexOption.IGNORE_CASE)
    private val RECEIVED = Regex("^recebi\\s+([\\d.,]+)(?:\\s+taxa\\s+([\\d.,]+))?$", RegexOption.IGNORE_CASE)
    private val SPENT = Regex("^gastei\\s+([\\d.,]+)\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val PLAN_SPEND = Regex("^planear\\s+gasto\\s+([\\d.,]+)\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val GOAL = Regex("^meta\\s+([\\d.,]+)$", RegexOption.IGNORE_CASE)
    private val SKILLS = Regex("^habilidades\\s+(.+)$", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Command {
        val s = raw.trim().replace(Regex("\\s+"), " ")
        if (s.isEmpty()) return Command.Help
        val low = s.lowercase()
        return when {
            low == "status" -> Command.Status
            low == "ajuda" || low == "help" -> Command.Help
            low == "empresa pronta" -> Command.CompanyReady
            low == "plano aprovado" -> Command.PlanApproved
            DECIDE.matches(s) -> {
                val m = DECIDE.find(s)!!.groupValues
                Command.Decide(m[2].toLong(), approve = m[1].lowercase() == "aprovar")
            }
            RECEIVED.matches(s) -> {
                val m = RECEIVED.find(s)!!.groupValues
                val gross = Money.parse(m[1]) ?: return Command.Invalid("valor inválido")
                val fee = if (m[2].isBlank()) Cents(0) else (Money.parse(m[2]) ?: return Command.Invalid("taxa inválida"))
                Command.Received(gross, fee)
            }
            PLAN_SPEND.matches(s) -> {
                val m = PLAN_SPEND.find(s)!!.groupValues
                val amount = Money.parse(m[1]) ?: return Command.Invalid("valor inválido")
                Command.PlanSpend(amount, m[2].trim())
            }
            SPENT.matches(s) -> {
                val m = SPENT.find(s)!!.groupValues
                val amount = Money.parse(m[1]) ?: return Command.Invalid("valor inválido")
                Command.SpentReal(amount, m[2].trim())
            }
            GOAL.matches(s) -> {
                val amount = Money.parse(GOAL.find(s)!!.groupValues[1]) ?: return Command.Invalid("valor inválido")
                Command.Goal(amount)
            }
            SKILLS.matches(s) -> {
                val list = SKILLS.find(s)!!.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }
                Command.Skills(list)
            }
            else -> Command.Invalid("não entendi: \"$s\". Escreve \"ajuda\" para ver os comandos.")
        }
    }

    val HELP = """
        status — estado do cofre, fase e pendências
        aprovar N / negar N — decide o pedido #N
        recebi X [taxa Y] — registra dinheiro recebido (só conta quando escreves isto)
        gastei X nota — registra um gasto que já fizeste (não bloqueia; o app não paga nada)
        planear gasto X nota — pede aprovação de um gasto (com tetos)
        plano aprovado — confirma o plano de micro negócio
        empresa pronta — ativa a fase 3 (divisão 30/30/40)
        meta X — define a meta do cofre
        habilidades a, b, c — as tuas habilidades para as propostas
    """.trimIndent()
}
