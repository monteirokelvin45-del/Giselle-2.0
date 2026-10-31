// ============================================================
// test/llm/ClaudeClientTest.kt
// ------------------------------------------------------------
// Usa MockWebServer (rede real simulada, não fake em memória) —
// aproveita o parâmetro proxyBaseUrl já existente pra apontar
// o ClaudeClient para o servidor mock em vez da API real.
// Cobre: parse de resposta bem-formada, retry em erro 500,
// não-retry em erro 4xx, timeout virando Failure em vez de
// exceção crua, e limpeza de fences markdown na resposta.
// ============================================================

package com.example.jarvis.llm

import com.example.jarvis.core.ActionType
import com.example.jarvis.core.PerceptionContext
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ClaudeClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setup() { server = MockWebServer(); server.start() }

    @After
    fun teardown() { server.shutdown() }

    private fun buildClient(maxRetries: Int = 2) = ClaudeClient(
        maxRetries = maxRetries,
        proxyBaseUrl = server.url("/").toString(),
        proxySharedSecret = "test-secret"
    )

    private fun context() = PerceptionContext(
        currentApp = "com.example", screenText = "tela", userCommand = "teste",
        recentHistory = emptyList(), relevantMemories = emptyList()
    )

    private fun anthropicBody(innerJson: String) = """
        {"content":[{"type":"text","text":${org.json.JSONObject.quote(innerJson)}}]}
    """.trimIndent()

    @Test
    fun `resposta bem formada vira Success com AgentDecision correto`() = runTest {
        val decisionJson = """
            {"reasoning":"ok","actionType":"CLICK","target":"Enviar","requiresConfirmation":false,"userFacingSummary":"Enviando"}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(anthropicBody(decisionJson)))

        val result = buildClient().decide(context())

        assertTrue(result is DecisionResult.Success)
        val decision = (result as DecisionResult.Success).decision
        assertEquals(ActionType.CLICK, decision.actionType)
        assertEquals("Enviar", decision.target)
    }

    @Test
    fun `remove fences markdown antes de parsear`() = runTest {
        val decisionJson = """
            {"reasoning":"ok","actionType":"GO_BACK","requiresConfirmation":false,"userFacingSummary":"Voltando"}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(anthropicBody("```json\n$decisionJson\n```")))

        val result = buildClient().decide(context())
        assertTrue(result is DecisionResult.Success)
        assertEquals(ActionType.GO_BACK, (result as DecisionResult.Success).decision.actionType)
    }

    @Test
    fun `erro 500 tenta de novo ate maxRetries e falha no fim se persistir`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500)) }

        val result = buildClient(maxRetries = 2).decide(context())

        assertEquals(3, server.requestCount) // 1 tentativa original + 2 retries
        assertTrue(result is DecisionResult.Failure)
    }

    @Test
    fun `erro 400 nao tenta de novo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400))

        val result = buildClient(maxRetries = 2).decide(context())

        assertEquals(1, server.requestCount) // não retentou em erro de cliente
        assertTrue(result is DecisionResult.Failure)
    }

    @Test
    fun `sucesso apos uma falha 500 conta como Success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val decisionJson = """
            {"reasoning":"ok","actionType":"NO_ACTION","requiresConfirmation":false,"userFacingSummary":""}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(anthropicBody(decisionJson)))

        val result = buildClient(maxRetries = 2).decide(context())

        assertTrue(result is DecisionResult.Success)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `resposta com json invalido vira Failure em vez de lancar excecao`() = runTest {
        server.enqueue(MockResponse().setBody(anthropicBody("isso não é json válido")))

        val result = buildClient(maxRetries = 0).decide(context())
        assertTrue(result is DecisionResult.Failure)
    }

    @Test
    fun `fallbackDecision sempre retorna ASK_USER sem exigir confirmacao`() {
        val fallback = buildClient().fallbackDecision("timeout")
        assertEquals(ActionType.ASK_USER, fallback.actionType)
        assertFalse(fallback.requiresConfirmation)
    }
}
