// ============================================================
// test/core/CircuitBreakerTest.kt
// ------------------------------------------------------------
// Testa as transições de estado do CircuitBreaker (CLOSED ->
// OPEN -> HALF_OPEN -> CLOSED/OPEN de novo) e a janela deslizante
// do RateLimiter. Usa durações pequenas (dezenas de ms) em vez de
// mockar tempo — mais simples e ainda determinístico o suficiente
// para CI, já que as margens usadas são bem folgadas.
// ============================================================

package com.example.jarvis.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CircuitBreakerTest {

    @Test
    fun `circuito comeca fechado e permite requisicoes`() = runBlocking {
        val breaker = CircuitBreaker(failureThreshold = 3, openDurationMs = 1000)
        assertEquals(CircuitState.CLOSED, breaker.currentState())
        assertTrue(breaker.allowRequest())
    }

    @Test
    fun `abre o circuito apos atingir o limite de falhas consecutivas`() = runBlocking {
        val breaker = CircuitBreaker(failureThreshold = 3, openDurationMs = 5000)
        breaker.recordFailure()
        breaker.recordFailure()
        assertEquals(CircuitState.CLOSED, breaker.currentState()) // ainda não bateu o limite

        breaker.recordFailure() // 3ª falha consecutiva
        assertEquals(CircuitState.OPEN, breaker.currentState())
    }

    @Test
    fun `circuito aberto bloqueia novas requisicoes dentro da janela`() = runBlocking {
        val breaker = CircuitBreaker(failureThreshold = 1, openDurationMs = 5000)
        breaker.recordFailure()
        assertEquals(CircuitState.OPEN, breaker.currentState())
        assertFalse(breaker.allowRequest())
    }

    @Test
    fun `sucesso reseta o circuito para fechado e zera falhas`() = runBlocking {
        val breaker = CircuitBreaker(failureThreshold = 3, openDurationMs = 5000)
        breaker.recordFailure()
        breaker.recordFailure()
        breaker.recordSuccess()
        assertEquals(CircuitState.CLOSED, breaker.currentState())

        // como as falhas foram zeradas, precisa de 3 novas falhas pra abrir de novo
        breaker.recordFailure()
        breaker.recordFailure()
        assertEquals(CircuitState.CLOSED, breaker.currentState())
    }

    @Test
    fun `apos o tempo de abertura passar, permite uma tentativa de teste (half-open)`() = runBlocking {
        val breaker = CircuitBreaker(failureThreshold = 1, openDurationMs = 50)
        breaker.recordFailure()
        assertEquals(CircuitState.OPEN, breaker.currentState())

        delay(80) // espera passar o openDurationMs

        assertTrue(breaker.allowRequest()) // deve permitir a tentativa de teste
        assertEquals(CircuitState.HALF_OPEN, breaker.currentState())
    }

    @Test
    fun `falha durante half-open reabre o circuito imediatamente`() = runBlocking {
        val breaker = CircuitBreaker(failureThreshold = 1, openDurationMs = 50)
        breaker.recordFailure()
        delay(80)
        breaker.allowRequest() // entra em HALF_OPEN
        assertEquals(CircuitState.HALF_OPEN, breaker.currentState())

        breaker.recordFailure() // falhou de novo durante o teste
        assertEquals(CircuitState.OPEN, breaker.currentState())
    }

    @Test
    fun `sucesso durante half-open fecha o circuito`() = runBlocking {
        val breaker = CircuitBreaker(failureThreshold = 1, openDurationMs = 50)
        breaker.recordFailure()
        delay(80)
        breaker.allowRequest() // entra em HALF_OPEN
        breaker.recordSuccess()
        assertEquals(CircuitState.CLOSED, breaker.currentState())
    }
}

class RateLimiterTest {

    @Test
    fun `permite chamadas ate o limite da janela`() = runBlocking {
        val limiter = RateLimiter(maxCallsPerWindow = 3, windowMs = 5000)
        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())
    }

    @Test
    fun `bloqueia apos atingir o limite dentro da janela`() = runBlocking {
        val limiter = RateLimiter(maxCallsPerWindow = 2, windowMs = 5000)
        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire()) // 3ª chamada estoura o limite
    }

    @Test
    fun `libera novas chamadas apos a janela expirar`() = runBlocking {
        val limiter = RateLimiter(maxCallsPerWindow = 1, windowMs = 50)
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())

        delay(80) // espera a janela expirar

        assertTrue(limiter.tryAcquire())
    }

    @Test
    fun `callsRemaining reflete o consumo atual da janela`() = runBlocking {
        val limiter = RateLimiter(maxCallsPerWindow = 5, windowMs = 5000)
        assertEquals(5, limiter.callsRemaining())
        limiter.tryAcquire()
        limiter.tryAcquire()
        assertEquals(3, limiter.callsRemaining())
    }
}
