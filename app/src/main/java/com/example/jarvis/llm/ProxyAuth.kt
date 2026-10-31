// ============================================================
// llm/ProxyAuth.kt
// ------------------------------------------------------------
// Assina requisições pro backend-proxy (worker.js) com HMAC-SHA256
// sobre timestamp+nonce+corpo — mesmo esquema que o Worker verifica
// do lado servidor. Mais forte que só mandar um shared secret fixo
// no header: cada requisição tem uma assinatura única, com janela
// de validade de 5 minutos e nonce anti-replay.
// ============================================================

package com.example.jarvis.llm

import android.content.Context
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object ProxyAuth {

    fun clientId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences("proxy_auth", Context.MODE_PRIVATE)
        val saved = prefs.getString("client_id", null)
        if (!saved.isNullOrBlank()) return saved
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("client_id", created).apply()
        return created
    }

    /** HMAC-SHA256 de [msg] usando [secret] como chave. Único ponto que calcula HMAC — sem duplicação. */
    private fun hmacHex(secret: ByteArray, msg: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(secret, "HmacSHA256"))
        }
        return mac.doFinal(msg.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Monta uma requisição POST assinada para [url], com corpo [body],
     * usando [sharedSecret] (o mesmo valor configurado no Worker via
     * `wrangler secret put PROXY_SHARED_SECRET`).
     */
    fun signedRequest(
        url: String,
        body: String,
        sharedSecret: String,
        clientId: String = "jarvis-installation"
    ): Request {
        val ts = (System.currentTimeMillis() / 1000).toString()
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }

        val signature = hmacHex(sharedSecret.toByteArray(Charsets.UTF_8), "$ts.$nonce.$clientId.$body")

        return Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .addHeader("x-timestamp", ts)
            .addHeader("x-nonce", nonce)
            .addHeader("x-client-id", clientId)
            .addHeader("x-signature", signature)
            .build()
    }
}
