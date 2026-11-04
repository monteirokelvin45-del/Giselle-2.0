// business/Tier.kt — nível de operação por bateria e saúde do modelo.
package com.example.jarvis.business

enum class Tier(val intervalHours: Long, val useAi: Boolean) {
    NORMAL(1, true),
    LOW(3, true),
    CRITICAL(6, false),
}

object TierPolicy {
    const val MODEL_ERRORS_BEFORE_DEGRADED = 3

    fun of(batteryPct: Int, modelErrors: Int): Tier = when {
        batteryPct < 15 -> Tier.CRITICAL
        batteryPct < 40 || modelErrors >= MODEL_ERRORS_BEFORE_DEGRADED -> Tier.LOW
        else -> Tier.NORMAL
    }
}
