// business/LlmClient.kt — a IA do módulo de negócio. Fala com o Worker (proxy assinado),
// nunca com o OpenRouter diretamente: nenhuma chave de API sai do Worker.
package com.example.jarvis.business

import com.example.jarvis.llm.ProxyAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface Brain {
    /** Devolve o texto, ou null em caso de falha. */
    suspend fun ask(system: String, user: String, maxTokens: Int): String?
}

class ProxyBrain(
    private val proxyBaseUrl: String?,
    private val proxySharedSecret: String?,
    private val clientId: String = "jarvis-installation",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
) : Brain {

    override suspend fun ask(system: String, user: String, maxTokens: Int): String? = withContext(Dispatchers.IO) {
        val url = proxyBaseUrl?.takeIf { it.isNotBlank() } ?: return@withContext null
        val secret = proxySharedSecret?.takeIf { it.isNotBlank() } ?: return@withContext null
        val body = JSONObject()
            .put("max_tokens", maxTokens)
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
            .toString()
        runCatching {
            client.newCall(ProxyAuth.signedRequest(url, body, secret, clientId)).execute().use { r ->
                if (!r.isSuccessful) null
                else JSONObject(r.body?.string().orEmpty())
                    .optJSONArray("content")?.optJSONObject(0)
                    ?.optString("text")?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }
}

/** Conta falhas seguidas do modelo (para o Tier) e aplica-as em todas as chamadas. */
class BrainGuard(private val store: BizStore, private val brain: Brain) {
    suspend fun ask(system: String, user: String, maxTokens: Int): String? {
        val reply = brain.ask(system, user, maxTokens)
        val errors = if (reply == null) (store.getKv(BizKeys.MODEL_ERRORS)?.toIntOrNull() ?: 0) + 1 else 0
        store.setKv(BizKeys.MODEL_ERRORS, errors.toString())
        return reply
    }

    fun modelErrors(): Int = store.getKv(BizKeys.MODEL_ERRORS)?.toIntOrNull() ?: 0
}

/** Corta um texto nas primeiras [n] palavras. */
internal fun clampWords(text: String, n: Int): String =
    text.trim().split(Regex("\\s+")).take(n).joinToString(" ")
