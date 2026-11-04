// ============================================================
// memory/ProxyEmbedder.kt
// ------------------------------------------------------------
// Embeddings reais para a memória semântica. O APK NÃO tem chave
// de embeddings: pede ao Worker (op "embed", ver backend-proxy/tools.js),
// que usa o binding AI do Cloudflare Workers. A chamada é assinada com HMAC
// como as demais.
//
// Sem proxy configurado, ou em caso de falha, devolve vetor vazio:
// a memória continua sendo guardada, mas não entra em buscas semânticas
// até um novo embedding ser feito. Nunca usa hash como substituto.
// ============================================================

package com.example.jarvis.memory

import com.example.jarvis.llm.ProxyAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ProxyEmbedder(
    private val proxyBaseUrl: String?,
    private val proxySharedSecret: String?,
    private val clientId: String = "jarvis-installation",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) : TextEmbedder {

    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.IO) {
        val url = proxyBaseUrl?.takeIf { it.isNotBlank() } ?: return@withContext FloatArray(0)
        val secret = proxySharedSecret?.takeIf { it.isNotBlank() } ?: return@withContext FloatArray(0)
        val body = JSONObject().put("op", "embed").put("text", text).toString()
        runCatching {
            client.newCall(ProxyAuth.signedRequest(url, body, secret, clientId)).execute().use { response ->
                if (!response.isSuccessful) FloatArray(0)
                else parseEmbedding(response.body?.string().orEmpty())
            }
        }.getOrElse { FloatArray(0) }
    }

    companion object {
        /** Lê {"embedding":[...]} . Qualquer formato inesperado vira vetor vazio. */
        internal fun parseEmbedding(json: String): FloatArray {
            val arr = runCatching { JSONObject(json).optJSONArray("embedding") }.getOrNull()
                ?: return FloatArray(0)
            return FloatArray(arr.length()) { i -> arr.optDouble(i).toFloat() }
        }
    }
}
