// ============================================================
// selfupdate/ClaudeDiagnosisLlmPort.kt
// ------------------------------------------------------------
// Implementação real do DiagnosisLlmPort, sobre o ClaudeClient
// já existente no núcleo — sem duplicar HTTP client/retry, usa
// ClaudeClient.sendRaw() (adicionado para este caso: diagnóstico
// de erro não é um AgentDecision de tela, é texto/diff livre).
// ============================================================

package com.example.jarvis.selfupdate

import com.example.jarvis.llm.ClaudeClient

class ClaudeDiagnosisLlmPort(private val client: ClaudeClient) : DiagnosisLlmPort {

    companion object {
        private val SYSTEM_PROMPT = """
            Você é um engenheiro Android/Kotlin sênior diagnosticando um
            erro real de produção do app Jarvis. Responda ESTRITAMENTE em
            JSON, sem markdown, sem texto fora do JSON, seguindo o schema
            exato pedido no prompt do usuário.
        """.trimIndent()
    }

    override suspend fun diagnosticar(prompt: String): String =
        client.sendRaw(systemPrompt = SYSTEM_PROMPT, userPrompt = prompt)
}
