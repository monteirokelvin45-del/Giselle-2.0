package com.example.jarvis.memory

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyEmbedderTest {

    @Test fun `le vetor do formato do worker`() {
        val v = ProxyEmbedder.parseEmbedding("""{"embedding":[0.5,-1,2.25],"dims":3,"model":"m"}""")
        assertEquals(3, v.size)
        assertEquals(0.5f, v[0], 1e-6f)
        assertEquals(-1f, v[1], 1e-6f)
    }

    @Test fun `formato inesperado vira vetor vazio, nunca exceção`() {
        assertEquals(0, ProxyEmbedder.parseEmbedding("""{"error":"x"}""").size)
        assertEquals(0, ProxyEmbedder.parseEmbedding("não é json").size)
        assertEquals(0, ProxyEmbedder.parseEmbedding("").size)
    }

    @Test fun `sem proxy configurado não faz chamada e devolve vazio`() = runBlocking {
        val e = ProxyEmbedder(proxyBaseUrl = "", proxySharedSecret = "segredo")
        assertTrue(e.embed("oi").isEmpty())
        val semSegredo = ProxyEmbedder(proxyBaseUrl = "https://x.test", proxySharedSecret = null)
        assertTrue(semSegredo.embed("oi").isEmpty())
    }
}
