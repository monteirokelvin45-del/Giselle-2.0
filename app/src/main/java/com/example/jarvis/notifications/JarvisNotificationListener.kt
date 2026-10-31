package com.example.jarvis.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.jarvis.core.GiselleBus

/**
 * Notificações inteligentes: guarda numa caixa, reage no avatar, anuncia chamadas
 * e responde a mensagens pela própria notificação (resposta rápida) — sem abrir o app.
 */
class JarvisNotificationListener : NotificationListenerService() {
    companion object {
        private const val TAG = "JarvisNotifListener"
        @Volatile var instance: JarvisNotificationListener? = null
    }

    override fun onListenerConnected() { super.onListenerConnected(); instance = this }
    override fun onListenerDisconnected() { instance = null; super.onListenerDisconnected() }

    private fun label(pkg: String) = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun isIncomingCall(n: Notification): Boolean =
        n.category == Notification.CATEGORY_CALL &&
            (n.extras.getInt("android.callType", 0) == 1 || n.fullScreenIntent != null)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val n = sbn.notification
        if (sbn.packageName == packageName) return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        if (isIncomingCall(n)) {
            GiselleBus.announce("Chamada de ${title.ifBlank { "número desconhecido" }}")
            return
        }
        if (sbn.isOngoing || (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (title.isBlank() && text.isBlank()) return

        val app = label(sbn.packageName)
        NotificationInbox.add(NotifItem(sbn.packageName, app, title, text, System.currentTimeMillis()))
        GiselleBus.notified("$app: ${title.ifBlank { text }}".take(80))
        Log.d(TAG, "Notificação de $app")
    }

    private fun replyAction(n: Notification) =
        n.actions?.firstOrNull { a -> a.remoteInputs?.isNotEmpty() == true }

    /** Responde (resposta rápida) à notificação mais recente cujo remetente/app contém [target]. */
    fun reply(target: String, text: String): Boolean = try {
        val sbn = activeNotifications.filter { !it.isOngoing && replyAction(it.notification) != null }
            .sortedByDescending { it.postTime }
            .firstOrNull { s ->
                val t = s.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                t.contains(target, true) || label(s.packageName).contains(target, true)
            }
        val act = sbn?.let { replyAction(it.notification) }
        if (act == null) false else {
            val ri = act.remoteInputs
            val intent = Intent()
            val b = Bundle().apply { ri.forEach { putCharSequence(it.resultKey, text) } }
            RemoteInput.addResultsToIntent(ri, intent, b)
            act.actionIntent.send(this, 0, intent)
            true
        }
    } catch (e: Exception) { false }

    /** Atende/recusa a chamada a tocar usando os botões da notificação (sem permissões extra). */
    fun callAction(answer: Boolean): Boolean = try {
        val rx = if (answer) Regex("atender|responder|answer|accept", RegexOption.IGNORE_CASE)
        else Regex("recusar|rejeitar|declinar|decline|reject|desligar|hang|end", RegexOption.IGNORE_CASE)
        val act = activeNotifications.firstOrNull { it.notification.category == Notification.CATEGORY_CALL }
            ?.notification?.actions?.firstOrNull { rx.containsMatchIn(it.title ?: "") }
        act?.actionIntent?.send(); act != null
    } catch (e: Exception) { false }
}
