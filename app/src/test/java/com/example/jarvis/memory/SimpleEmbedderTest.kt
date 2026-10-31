// ============================================================
// test/memory/SimpleEmbedderTest.kt
// ------------------------------------------------------------
// Cobre o que é testável sem rede/Android: determinismo do
// DevFallbackEmbedder (mesma entrada -> mesmo vetor, sempre —
// senão a busca por similaridade no MemoryStore fica instável
// entre chamadas). Chamadas de embeddings não usam credenciais no cliente.
// real, não faz sentido mockar HTTP aqui com o orçamento atual.
// ============================================================

package com.example.jarvis.memory

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SimpleEmbedderTest {

    @Test
    fun `mesmo texto sempre gera o mesmo embedding`() = runTest {
        val embedder = DevFallbackEmbedder()
        val a = embedder.embed("treino de peito")
        val b = embedder.embed("treino de peito")
        assertArrayEquals(a, b, 0.0001f)
    }

    @Test
    fun `textos diferentes geram embeddings diferentes`() = runTest {
        val embedder = DevFallbackEmbedder()
        val a = embedder.embed("treino de peito")
        val b = embedder.embed("estudo de kotlin")
        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun `case insensitive - maiuscula e minuscula geram o mesmo embedding`() = runTest {
        val embedder = DevFallbackEmbedder()
        val a = embedder.embed("Treino De Peito")
        val b = embedder.embed("treino de peito")
        assertArrayEquals(a, b, 0.0001f)
    }

    @Test
    fun `embedding tem 64 dimensoes`() = runTest {
        val embedder = DevFallbackEmbedder()
        assertEquals(64, embedder.embed("qualquer texto").size)
    }
}
