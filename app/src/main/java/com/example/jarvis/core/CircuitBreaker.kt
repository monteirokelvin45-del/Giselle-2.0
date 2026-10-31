// ============================================================
// core/CircuitBreaker.kt
// ------------------------------------------------------------
// Duas proteções distintas no mesmo arquivo, porque resolvem
// o mesmo risco raiz (o agente agindo rápido e errado em loop):
//
// 1. RateLimiter — limita QUANTAS decisões por minuto o
//    orquestrador pode processar, evitando spam de chamadas à
//    API (custo) e ações em sequência descontrolada na tela.
//
// 2. CircuitBreaker — se as últimas N execuções falharam,
//    ABRE o circuito e para de tentar agir por um tempo,
//    evitando um loop de "decide errado -> falha -> tenta de
//    novo -> falha de novo" que pode destravar em algo pior
//    numa tela sensível.
// ============================================================

package com.example.jarvis.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// -----------------------------------------------------
// Rate limiter simples de janela deslizante
// -----------------------------------------------------
class RateLimiter(
    private val maxCallsPerWindow: Int = 20,
    private val windowMs: Long = 60_000L
) {
    private val timestamps = ArrayDeque<Long>()
    private val mutex = Mutex()

    suspend fun tryAcquire(): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        while (timestamps.isNotEmpty() && now - timestamps.first() > windowMs) {
            timestamps.removeFirst()
        }
        if (timestamps.size >= maxCallsPerWindow) {
            false
        } else {
            timestamps.addLast(now)
            true
        }
    }

    suspend fun callsRemaining(): Int = mutex.withLock {
        val now = System.currentTimeMillis()
        while (timestamps.isNotEmpty() && now - timestamps.first() > windowMs) {
            timestamps.removeFirst()
        }
        (maxCallsPerWindow - timestamps.size).coerceAtLeast(0)
    }
}

// -----------------------------------------------------
// Circuit breaker de 3 estados clássico
// -----------------------------------------------------
enum class CircuitState { CLOSED, OPEN, HALF_OPEN }

class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val openDurationMs: Long = 60_000L
) {
    private var state = CircuitState.CLOSED
    private var consecutiveFailures = 0
    private var openedAt: Long = 0
    private val mutex = Mutex()

    /** Chamar ANTES de tentar uma decisão/execução. */
    suspend fun allowRequest(): Boolean = mutex.withLock {
        when (state) {
            CircuitState.CLOSED -> true
            CircuitState.OPEN -> {
                if (System.currentTimeMillis() - openedAt > openDurationMs) {
                    state = CircuitState.HALF_OPEN
                    true // permite 1 tentativa de teste
                } else {
                    false
                }
            }
            CircuitState.HALF_OPEN -> true
        }
    }

    suspend fun recordSuccess() = mutex.withLock {
        consecutiveFailures = 0
        state = CircuitState.CLOSED
    }

    suspend fun recordFailure() = mutex.withLock {
        consecutiveFailures++
        if (consecutiveFailures >= failureThreshold || state == CircuitState.HALF_OPEN) {
            state = CircuitState.OPEN
            openedAt = System.currentTimeMillis()
        }
    }

    suspend fun currentState(): CircuitState = mutex.withLock { state }
}
