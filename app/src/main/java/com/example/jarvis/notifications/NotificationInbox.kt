package com.example.jarvis.notifications

data class NotifItem(val pkg: String, val app: String, val title: String, val text: String, val time: Long)

/** Caixa de entrada em memória (últimas 50) para resumos por voz. */
object NotificationInbox {
    private val items = ArrayDeque<NotifItem>()

    @Synchronized fun add(i: NotifItem) { items.addLast(i); while (items.size > 50) items.removeFirst() }
    @Synchronized fun clear() = items.clear()

    @Synchronized fun digest(): String {
        val cutoff = System.currentTimeMillis() - 12 * 3600_000L
        val recent = items.filter { it.time >= cutoff }
        if (recent.isEmpty()) return "Sem notificações novas."
        val groups = recent.groupBy { it.title.ifBlank { it.app } to it.app }.entries
            .sortedByDescending { it.value.size }.take(5)
        val head = "Tens ${recent.size} notificações. "
        return head + groups.joinToString("; ") { (k, v) ->
            "${k.first} (${k.second}${if (v.size > 1) ", ${v.size}" else ""}): ${v.last().text.take(60)}"
        }
    }
}
