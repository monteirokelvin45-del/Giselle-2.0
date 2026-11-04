// business/Money.kt — todo o dinheiro do módulo de negócio em centavos inteiros (sem Double).
package com.example.jarvis.business

import kotlin.math.abs

@JvmInline
value class Cents(val value: Long) {
    operator fun plus(other: Cents) = Cents(value + other.value)
    operator fun minus(other: Cents) = Cents(value - other.value)

    fun format(): String {
        val sign = if (value < 0) "-" else ""
        val a = abs(value)
        return "USD $sign${a / 100}.${(a % 100).toString().padStart(2, '0')}"
    }
}

object Money {
    private val AMOUNT = Regex("""\d{1,9}([.,]\d{1,2})?""")

    /** "12", "12.5", "12,50" -> centavos. Qualquer outra coisa devolve null. */
    fun parse(text: String): Cents? {
        val t = text.trim()
        if (!AMOUNT.matches(t)) return null
        val parts = t.replace(',', '.').split('.')
        val whole = parts[0].toLong()
        val frac = if (parts.size == 2) parts[1].padEnd(2, '0').toLong() else 0L
        return Cents(whole * 100 + frac)
    }
}
