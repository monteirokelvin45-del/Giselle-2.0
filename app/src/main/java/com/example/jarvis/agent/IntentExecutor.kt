// ============================================================
// agent/IntentExecutor.kt — Fase 1 (Giselle)
// Executor nativo: APIs oficiais do Android, sem acessibilidade.
// ============================================================
package com.example.jarvis.agent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.jarvis.core.ActionType
import com.example.jarvis.core.AgentDecision
import com.example.jarvis.routine.RoutineScheduler
import android.os.Build
import android.telecom.TelecomManager
import com.example.jarvis.ledger.Ledger
import com.example.jarvis.notifications.JarvisNotificationListener
import com.example.jarvis.skills.SkillRecorder
import com.example.jarvis.visual.OverlayController

interface IntentPort {
    fun execute(decision: AgentDecision): Boolean
}

class IntentExecutor(private val context: Context) : IntentPort {

    override fun execute(decision: AgentDecision): Boolean = try {
        val t = decision.target?.trim().orEmpty()
        val text = decision.inputText?.trim().orEmpty()
        when (decision.actionType) {
            ActionType.CALL -> call(t)
            ActionType.SEND_SMS -> sms(t, text)
            ActionType.SET_ALARM -> alarm(t, text)
            ActionType.SET_TIMER -> timer(t, text)
            ActionType.OPEN_URL -> openUrl(t)
            ActionType.SET_VOLUME -> volume(t)
            ActionType.SET_ROUTINE -> routine(t, text)
            ActionType.REPLY_NOTIFICATION -> JarvisNotificationListener.instance?.reply(t, text) ?: false
            ActionType.ANSWER_CALL -> callControl(true)
            ActionType.REJECT_CALL -> callControl(false)
            ActionType.LOG_EXPENSE -> expense(t, text)
            ActionType.LEARN_SKILL -> learn(t)
            ActionType.SET_SKIN -> { OverlayController.setSkin(t); true }
            ActionType.MOBILE_MONEY -> mobileMoney(t)
            ActionType.STUDY_MODE -> study(t, text)
            else -> false
        }
    } catch (e: Exception) {
        false
    }

    private fun start(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    /** Número direto ou busca por nome nos contatos (precisa de READ_CONTACTS). */
    private fun resolveNumber(target: String): String? {
        val digits = target.replace(Regex("[\\s\\-()]"), "")
        if (digits.matches(Regex("\\+?\\d{3,15}"))) return digits
        if (target.isBlank() || !granted(Manifest.permission.READ_CONTACTS)) return null
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$target%"),
            null
        )?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return null
    }

    private fun call(target: String): Boolean {
        val number = resolveNumber(target) ?: return false
        val action = if (granted(Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        return start(Intent(action, Uri.parse("tel:$number")))
    }

    /** Abre o compositor de SMS preenchido; o utilizador toca em enviar (mais seguro). */
    private fun sms(target: String, body: String): Boolean {
        val number = resolveNumber(target) ?: return false
        return start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).putExtra("sms_body", body))
    }

    private fun alarm(hhmm: String, label: String): Boolean {
        val m = Regex("(\\d{1,2}):(\\d{2})").find(hhmm) ?: return false
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h !in 0..23 || min !in 0..59) return false
        return start(
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, min)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Giselle" })
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
    }

    private fun timer(seconds: String, label: String): Boolean {
        val s = seconds.toIntOrNull()?.takeIf { it in 1..86_400 } ?: return false
        return start(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, s)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Giselle" })
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
    }

    private fun openUrl(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        return start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun callControl(answer: Boolean): Boolean {
        if (JarvisNotificationListener.instance?.callAction(answer) == true) return true
        if (!granted(Manifest.permission.ANSWER_PHONE_CALLS)) return false
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return if (answer) { tm.acceptRingingCall(); true }
        else if (Build.VERSION.SDK_INT >= 28) tm.endCall() else false
    }

    private fun expense(amount: String, note: String): Boolean {
        val a = Ledger.parseAmount(amount) ?: return false
        val p = note.split("|", limit = 2)
        Ledger.add(context, a, p[0], p.getOrElse(1) { "" })
        return true
    }

    private fun learn(t: String): Boolean {
        val p = t.split("|", limit = 2)
        return when (p[0].trim().lowercase()) {
            "start", "iniciar", "gravar" -> SkillRecorder.start(p.getOrElse(1) { "" })
            "stop", "parar", "fim" -> SkillRecorder.stop(context)
            else -> false
        }
    }

    /**
     * Dinheiro móvel (M-Pesa / e-Mola): só ABRE o discador com o código USSD preenchido.
     * Nunca liga sozinha e nunca digita PIN ou valores — o utilizador conclui no menu do operador.
     * Códigos configuráveis em SharedPreferences "giselle_cfg" (ussd_mpesa / ussd_emola).
     */
    private fun mobileMoney(service: String): Boolean {
        val prefs = context.getSharedPreferences("giselle_cfg", Context.MODE_PRIVATE)
        val code = when (service.lowercase().replace(Regex("[^a-z]"), "")) {
            "mpesa" -> prefs.getString("ussd_mpesa", "*150#")
            "emola" -> prefs.getString("ussd_emola", "*898#")
            else -> null
        }?.takeIf { it.matches(Regex("[*#0-9]{2,20}")) } ?: return false
        return start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code))))
    }

    /** Modo estudo: temporizador de foco (minutos em [t], padrão 25) com o rótulo [label]. */
    private fun study(minutes: String, label: String): Boolean {
        val m = minutes.toIntOrNull()?.takeIf { it in 1..180 } ?: 25
        return timer((m * 60).toString(), label.ifBlank { "Estudo" })
    }

    private fun routine(hhmm: String, text: String): Boolean =
        if (hhmm.equals("cancel", ignoreCase = true)) { RoutineScheduler.cancelAll(context); true }
        else RoutineScheduler.add(context, hhmm, text)

    private fun volume(pct: String): Boolean {
        val p = pct.removeSuffix("%").trim().toIntOrNull()?.coerceIn(0, 100) ?: return false
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, p * max / 100, AudioManager.FLAG_SHOW_UI)
        return true
    }
}
