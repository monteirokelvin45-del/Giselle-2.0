package com.example.jarvis.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.example.jarvis.core.Coordinates
import com.example.jarvis.llm.ClaudeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

interface VisionPort {
    /** Devolve o centro (em pixels do ecrã) do elemento descrito, ou null. */
    suspend fun locate(target: String): Coordinates?
    /** Descreve/traduz/explica o que está no ecrã (texto livre). */
    suspend fun describe(question: String): String?
}

/** Plano C: screenshot → LLM multimodal → coordenadas → toque. */
class VisionExecutor(private val client: ClaudeClient, private val context: Context) : VisionPort {

    override suspend fun locate(target: String): Coordinates? = withContext(Dispatchers.IO) {
        try {
            val bmp = ScreenCapture.capture() ?: return@withContext null
            val scaled = if (bmp.width > 768) {
                Bitmap.createScaledBitmap(bmp, 768, (bmp.height * 768f / bmp.width).toInt(), true)
            } else bmp
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            val raw = client.sendVision(prompt(target), b64)
            val s = raw.indexOf('{'); val e = raw.lastIndexOf('}')
            if (s < 0 || e <= s) return@withContext null
            val j = JSONObject(raw.substring(s, e + 1))
            if (!j.optBoolean("found", true)) return@withContext null
            val dm = context.resources.displayMetrics
            Coordinates(
                (j.getDouble("x") / 1000.0 * dm.widthPixels).toFloat(),
                (j.getDouble("y") / 1000.0 * dm.heightPixels).toFloat()
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun describe(question: String): String? = withContext(Dispatchers.IO) {
        try {
            val bmp = ScreenCapture.capture() ?: return@withContext null
            val scaled = if (bmp.width > 900) Bitmap.createScaledBitmap(bmp, 900, (bmp.height * 900f / bmp.width).toInt(), true) else bmp
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
            val ask = "Captura de ecrã de um telemóvel. Responde em português, em no máximo 4 frases curtas, " +
                "sem formatação. Pedido: " + question.ifBlank { "explica o que está no ecrã" }
            client.sendVision(ask, Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), json = false).trim().ifBlank { null }
        } catch (e: Exception) { null }
    }

    private fun prompt(target: String) =
        "Captura de ecrã de um telemóvel Android. Localiza o elemento: \"$target\". " +
            "Responde SÓ JSON: {\"found\":true|false,\"x\":0-1000,\"y\":0-1000} com o CENTRO do elemento em " +
            "coordenadas normalizadas (0,0 = canto superior esquerdo; 1000,1000 = inferior direito)."
}
