package com.example.jarvis.ledger

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/** Gastos por voz: um JSON por linha em filesDir/ledger.jsonl. Moeda: MT. */
object Ledger {
    private fun file(ctx: Context) = File(ctx.filesDir, "ledger.jsonl")

    fun parseAmount(s: String?): Double? =
        s?.replace(",", ".")?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()?.takeIf { it > 0 }

    fun add(ctx: Context, amount: Double, category: String, note: String) {
        file(ctx).appendText(
            JSONObject().put("ts", System.currentTimeMillis()).put("amount", amount)
                .put("category", category.ifBlank { "outros" }.lowercase()).put("note", note).toString() + "\n"
        )
    }

    private fun entries(ctx: Context, from: Long, to: Long): List<JSONObject> =
        file(ctx).takeIf { it.exists() }?.readLines().orEmpty()
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            .filter { it.getLong("ts") in from until to }

    fun total(ctx: Context, from: Long, to: Long) = entries(ctx, from, to).sumOf { it.getDouble("amount") }

    fun startOfDay(daysAgo: Int = 0): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun report(ctx: Context, period: String): String {
        val now = System.currentTimeMillis()
        val (from, label) = when (period.lowercase()) {
            "hoje", "today" -> startOfDay() to "hoje"
            "mes", "mês", "month" -> startOfDay(30) to "nos últimos 30 dias"
            else -> startOfDay(7) to "nos últimos 7 dias"
        }
        val list = entries(ctx, from, now + 1)
        if (list.isEmpty()) return "Sem gastos registados $label."
        val total = list.sumOf { it.getDouble("amount") }
        val top = list.groupBy { it.getString("category") }
            .mapValues { (_, v) -> v.sumOf { it.getDouble("amount") } }
            .entries.sortedByDescending { it.value }.take(3)
            .joinToString(", ") { "${it.key} ${"%.0f".format(it.value)}" }
        return "Gastaste ${"%.0f".format(total)} MT $label. Maiores: $top."
    }
}
