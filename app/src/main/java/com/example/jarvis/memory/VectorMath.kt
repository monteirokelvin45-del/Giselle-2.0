// ============================================================
// memory/VectorMath.kt
// ------------------------------------------------------------
// Extraído de MemoryStore para ser testável isoladamente, sem
// precisar de fakes de Room/DAO — é lógica matemática pura.
// ============================================================

package com.example.jarvis.memory

import kotlin.math.sqrt

object VectorMath {
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0f || normB == 0f) return 0f
        return dot / (sqrt(normA) * sqrt(normB))
    }
}
