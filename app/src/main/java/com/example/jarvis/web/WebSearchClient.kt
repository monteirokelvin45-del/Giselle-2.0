// ============================================================
// web/WebSearchClient.kt
// ------------------------------------------------------------
// Pesquisa na web com fontes, via Worker (op "search", ver backend-proxy/tools.js).
// Ação somente leitura: não mexe no telefone, então não passa pelo SafetyGate.
// Devolve um texto curto para falar, com os domínios das fontes no fim.
// Sem proxy, ou em caso de falha, devolve null e a ação é marcada como falhada.
// ============================================================

package com.example.jarvis.web

import com.example.jarvis.agent.WebSearchPort
import com.example.jarvis.llm.ProxyAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

class WebSearchClient(
    private val proxyBaseUrl: String?,
    private val proxySharedSecret: String?,
    private val clientId: String = "jarvis-installation",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : WebSearchPort {

    override suspend fun search(query: String): String? = withContext(Dispatchers.IO) {
        val url = proxyBaseUrl?.takeIf { it.isNotBlank() } ?: return@withContext null
        val secret = proxySharedSecret?.takeIf { it.isNotBlank() } ?: return@withContext null
        val body = JSONObject().put("op", "search").put("query", query).put("max", 3).toString()
        runCatching {
            client.newCall(ProxyAuth.signedRequest(url, body, secret, clientId)).execute().use { response ->
                if (!response.isSuccessful) null
                else formatForSpeech(parseResults(response.body?.string().orEmpty()))
            }
        }.getOrNull()
    }

    data class Hit(val title: String, val url: String, val snippet: String)

    companion object {
        internal fun parseResults(json: String): List<Hit> {
            val arr = runCatching { JSONObject(json).optJSONArray("results") }.getOrNull() ?: return emptyList()
            val out = mutableListOf<Hit>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val title = o.optString("title").trim()
                val url = o.optString("url").trim()
                val validUrl = url.startsWith("https://") || url.startsWith("http://")
                if (title.isEmpty() || !validUrl) continue
                out += Hit(title, url, o.optString("snippet").trim())
            }
            return out
        }

        /** Texto para voz: até 3 resultados, trecho curto, e no fim os domínios usados como fontes. */
        internal fun formatForSpeech(hits: List<Hit>): String? {
            if (hits.isEmpty()) return null
            val top = hits.take(3)
            val body = top.joinToString(" ") { h ->
                val s = h.snippet.take(160)
                if (s.isBlank()) "${h.title}." else "${h.title}: $s"
            }.take(550)
            val sources = top.map { domain(it.url) }.distinct().joinToString(", ")
            return "Encontrei ${hits.size} fontes. $body Fontes: $sources."
        }

        internal fun domain(url: String): String =
            runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
    }
}
