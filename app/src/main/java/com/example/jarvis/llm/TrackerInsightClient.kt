// ============================================================
// llm/TrackerInsightClient.kt
// ------------------------------------------------------------
// Igual ao ClaudeClient em robustez (timeout, retry, resultado
// explícito) mas com prompt e schema próprios do modo Tracker:
// a saída é sempre um TrackerInsight (sugestão), nunca uma ação
// executável. Arquivo separado do ClaudeClient porque o system
// prompt e o schema de resposta são fundamentalmente diferentes
// — misturar os dois criaria acoplamento entre "agir na tela"
// e "sugerir sobre dados", que devem ficar conceitualmente
// distantes.
// ============================================================

package com.example.jarvis.llm

import com.example.jarvis.tracker.agent.TrackerContext
import com.example.jarvis.tracker.agent.TrackerInsight
import com.example.jarvis.tracker.agent.TrackerInsightPrioridade
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Resultado próprio deste cliente: sucesso pode conter null (sem insight relevante agora). */
sealed class InsightDecisionResult {
    data class Success(val insight: TrackerInsight?) : InsightDecisionResult()
    data class Failure(val reason: String) : InsightDecisionResult()
}

class TrackerInsightClient(
    private val model: String = "claude-sonnet-4-6",
    private val maxRetries: Int = 2,
    private val proxyBaseUrl: String? = null,
    private val proxySharedSecret: String? = null,
    private val proxyClientId: String = "jarvis-installation"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    companion object {
        private val RETRYABLE_HTTP_CODES = setOf(408, 425, 429, 500, 502, 503, 504)

        private val SYSTEM_PROMPT = """
            Você é o motor de diagnóstico do módulo Tracker de um app pessoal
            de desenvolvimento (físico, mental, estudos, finanças). Você recebe
            um resumo agregado dos últimos 7 dias de registros CONFIRMADOS pelo
            usuário (nunca planos, só o que de fato aconteceu).

            Sua tarefa: identificar UM padrão relevante — positivo ou de alerta —
            e sugerir uma ação concreta. Você NUNCA tem permissão para alterar
            dados diretamente; você apenas sugere, em linguagem natural, o que o
            usuário poderia fazer.

            Se não houver nada genuinamente relevante para dizer (dados
            insuficientes, tudo dentro do esperado), responda com
            "temInsight": false — não force uma sugestão artificial só para
            preencher espaço.

            Responda ESTRITAMENTE em JSON, sem markdown, sem texto fora do JSON:
            {
              "temInsight": true|false,
              "tipo": "INSIGHT_PESO|INSIGHT_TREINO|INSIGHT_ESTUDO|INSIGHT_FINANCAS|INSIGHT_CONSISTENCIA|DIAGNOSTICO_GERAL",
              "dadosObservados": "resumo curto do que motivou (ou vazio se temInsight=false)",
              "sugestaoTexto": "texto natural em português, direto, sem jargão (ou vazio se temInsight=false)",
              "prioridade": "BAIXA|MEDIA|ALTA"
            }

            Regras:
            - Baseie-se só nos números fornecidos, nunca invente dados.
            - Não repita uma sugestão que já está na lista de "sugestões sem
              feedback ainda" — o usuário já viu, esperar reação antes de insistir.
            - Tom: direto e prático, como um bom personal/coach, nunca alarmista.
        """.trimIndent()
    }

    suspend fun generateInsight(context: TrackerContext): InsightDecisionResult {
        var lastError = "erro desconhecido"

        repeat(maxRetries + 1) { attempt ->
            try {
                val insight = performRequest(context)
                return InsightDecisionResult.Success(insight)
            } catch (e: IOException) {
                lastError = "Falha de rede: ${e.message}"
            } catch (e: IllegalStateException) {
                lastError = e.message ?: "Erro HTTP"
                val status = Regex("(?:HTTP|falhou:|falhou)\\s*(\\d{3})")
                    .find(lastError)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (status == null || status !in RETRYABLE_HTTP_CODES) {
                    return InsightDecisionResult.Failure(lastError)
                }
            } catch (e: Exception) {
                lastError = "Resposta inválida do modelo: ${e.message}"
            }
            if (attempt < maxRetries) delay(500L * (attempt + 1))
        }
        return InsightDecisionResult.Failure(lastError)
    }

    private fun performRequest(context: TrackerContext): TrackerInsight? {
        val userPrompt = buildUserPrompt(context)

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 512)
            put("system", SYSTEM_PROMPT)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
        }

        val url = proxyBaseUrl
        val secret = proxySharedSecret
        if (url.isNullOrBlank() || secret.isNullOrBlank()) {
            throw IllegalStateException("Proxy de IA não configurado; chamadas diretas e chaves no APK estão desativadas.")
        }
        val request = ProxyAuth.signedRequest(url, body.toString(), secret, proxyClientId)

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Claude API falhou: HTTP ${response.code} ${response.message}")
            }
            val raw = response.body?.string().orEmpty()
            return parseInsight(extractText(raw))
        }
    }

    private fun buildUserPrompt(ctx: TrackerContext): String = """
        PERFIL: ${ctx.perfilResumo}
        METAS: ${ctx.metasResumo}

        RESUMO DOS ÚLTIMOS 7 DIAS:
        ${ctx.janelaRecente}

        SUGESTÕES ANTERIORES AINDA SEM REAÇÃO DO USUÁRIO:
        ${ctx.eventosSemFeedback.joinToString("\n") { "- $it" }.ifBlank { "(nenhuma)" }}

        Gere o diagnóstico. Responda apenas o JSON.
    """.trimIndent()

    private fun extractText(raw: String): String {
        val obj = JSONObject(raw)
        val content = obj.getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "text") sb.append(block.optString("text"))
        }
        return sb.toString().trim()
    }

    private fun parseInsight(rawJson: String): TrackerInsight? {
        val cleaned = rawJson.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val obj = JSONObject(cleaned)
        if (!obj.optBoolean("temInsight", false)) return null

        return TrackerInsight(
            tipo = parseInsightType(obj.getString("tipo")),
            dadosObservados = obj.getString("dadosObservados"),
            sugestaoTexto = obj.getString("sugestaoTexto"),
            prioridade = TrackerInsightPrioridade.valueOf(obj.optString("prioridade", "MEDIA"))
        )
    }

    /** Nunca lança — um tipo desconhecido vindo do LLM vira DESCONHECIDO, explícito e bloqueável, não um crash nem um branch silencioso. */
    private fun parseInsightType(raw: String): TrackerInsightType = try {
        TrackerInsightType.valueOf(raw.uppercase())
    } catch (e: IllegalArgumentException) {
        TrackerInsightType.DESCONHECIDO
    }
}
