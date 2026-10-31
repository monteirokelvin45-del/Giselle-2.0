package com.example.jarvis.agent

import com.example.jarvis.vision.VisionPort
import kotlinx.coroutines.delay

/**
 * Receitas por app (WhatsApp/Telegram/Instagram): abre → pesquisa contacto → abre conversa
 * → escreve → envia. Cada toque tenta o texto/descrição do botão e, se falhar, a visão.
 */
class AppMessenger(
    private val launcher: AppLauncherPort,
    private val screen: ScreenActuator,
    private val vision: VisionPort?
) {
    private data class Recipe(
        val open: List<String> = emptyList(),
        val search: List<String>,
        val input: List<String>,
        val send: List<String>
    )

    private val recipes = mapOf(
        "whatsapp" to Recipe(search = listOf("Pesquisar", "Search"), input = listOf("Mensagem", "Message"), send = listOf("Enviar", "Send")),
        "telegram" to Recipe(search = listOf("Pesquisar", "Search"), input = listOf("Mensagem", "Message"), send = listOf("Enviar", "Send")),
        "instagram" to Recipe(
            open = listOf("Mensagens", "Messenger", "Direct"),
            search = listOf("Pesquisar", "Search"), input = listOf("Mensagem", "Message"), send = listOf("Enviar", "Send")
        )
    )

    fun supports(app: String) = recipes.containsKey(app.lowercase().trim())

    suspend fun send(app: String, contact: String, text: String): Boolean {
        val r = recipes[app.lowercase().trim()] ?: return false
        if (contact.isBlank() || text.isBlank()) return false
        if (!launcher.openByName(app)) return false
        delay(2200)
        if (r.open.isNotEmpty()) { tap(r.open); delay(900) }
        if (!tap(r.search)) return false
        delay(700)
        if (!screen.typeText(contact)) return false
        delay(1400)
        if (!tap(listOf(contact))) return false
        delay(1400)
        tap(r.input) // pode já estar focado; ignora falha
        if (!screen.typeText(text)) return false
        delay(500)
        return tap(r.send)
    }

    private suspend fun tap(labels: List<String>): Boolean {
        for (l in labels) if (screen.click(l)) return true
        val c = vision?.locate(labels.first()) ?: return false
        return screen.tapAt(c.x, c.y)
    }
}
