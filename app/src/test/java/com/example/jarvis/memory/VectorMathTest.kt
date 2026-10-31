package com.example.jarvis.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class VectorMathTest {
    @Test
    fun `vetores identicos tem similaridade 1`() {
        val v = floatArrayOf(1f, 2f, 3f)
        assertEquals(1f, VectorMath.cosineSimilarity(v, v), 0.0001f)
    }

    @Test
    fun `vetores ortogonais tem similaridade 0`() {
        assertEquals(0f, VectorMath.cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0.0001f)
    }

    @Test
    fun `tamanhos diferentes retorna 0 em vez de lancar excecao`() {
        assertEquals(0f, VectorMath.cosineSimilarity(floatArrayOf(1f), floatArrayOf(1f, 2f)), 0.0001f)
    }

    @Test
    fun `vetor zero retorna 0 em vez de dividir por zero`() {
        assertEquals(0f, VectorMath.cosineSimilarity(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)), 0.0001f)
    }
}
