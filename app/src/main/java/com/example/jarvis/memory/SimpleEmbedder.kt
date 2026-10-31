// ============================================================
// memory/SimpleEmbedder.kt
// ------------------------------------------------------------
// Nota de engenharia honesta: a API da Anthropic não expõe
// endpoint de embeddings nativamente. Duas opções reais:
//
// 1) Usar um provedor de embeddings dedicado (Voyage AI é o
//    parceiro recomendado pela Anthropic para isso).
// 2) Rodar um embedder local leve no device (ex: modelo
//    sentence-transformers convertido pra TFLite/ONNX).
//
// Este arquivo traz a opção (1) via Voyage, com fallback
// hash-based simplificado só para prototipagem offline —
// NÃO use o fallback em produção, ele é só pra você testar
// o pipeline sem depender de outra API key ainda.
// ============================================================

package com.example.jarvis.memory

import java.security.MessageDigest

/**
 * ATENÇÃO: apenas para desenvolvimento/teste sem API key extra.
 * Gera um pseudo-embedding determinístico via hashing — não captura
 * semântica real, só permite testar o pipeline de memória ponta a ponta
 * antes de plugar um embedder de verdade.
 */
class DevFallbackEmbedder : TextEmbedder {
    private val dimensions = 64

    override suspend fun embed(text: String): FloatArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.lowercase().toByteArray())
        return FloatArray(dimensions) { i ->
            val byte = digest[i % digest.size]
            (byte.toFloat() / 127f)
        }
    }
}
