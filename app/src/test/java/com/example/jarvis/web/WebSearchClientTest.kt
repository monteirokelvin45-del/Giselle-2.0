package com.example.jarvis.web

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchClientTest {

    @Test fun `le resultados e descarta URL inválida ou título vazio`() {
        val hits = WebSearchClient.parseResults(
            """{"provider":"brave","results":[
                {"title":"Clima","url":"https://www.meteo.test/x","snippet":"Chuva"},
                {"title":"","url":"https://vazio.test"},
                {"title":"Mau","url":"javascript:alert(1)"}
            ]}"""
        )
        assertEquals(1, hits.size)
        assertEquals("Clima", hits[0].title)
    }

    @Test fun `texto para voz tem fontes sem www e limite de trecho`() {
        val hits = listOf(
            WebSearchClient.Hit("A", "https://www.uol.test/a", "x".repeat(300)),
            WebSearchClient.Hit("B", "https://g1.test/b", ""),
        )
        val text = WebSearchClient.formatForSpeech(hits)!!
        assertTrue(text.startsWith("Encontrei 2 fontes."))
        assertTrue(text.endsWith("Fontes: uol.test, g1.test."))
        assertTrue(text.contains("B.")) // sem trecho: só o título
    }

    @Test fun `sem resultados ou sem proxy devolve null`() = runBlocking {
        assertNull(WebSearchClient.formatForSpeech(emptyList()))
        assertNull(WebSearchClient(proxyBaseUrl = null, proxySharedSecret = "s").search("clima"))
        assertNull(WebSearchClient(proxyBaseUrl = "https://x.test", proxySharedSecret = "").search("clima"))
    }
}
