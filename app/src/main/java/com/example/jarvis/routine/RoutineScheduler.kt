package com.example.jarvis.routine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.jarvis.core.JarvisForegroundService
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Rotinas diárias ("todo dia às 8h me lembre de…"): guardadas em prefs, agendadas via WorkManager. */
object RoutineScheduler {
    private const val PREFS = "giselle_routines"
    private const val KEY = "items"
    private const val TAG = "routine"

    fun add(ctx: Context, hhmm: String, text: String): Boolean {
        if (parse(hhmm) == null || text.isBlank()) return false
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putStringSet(KEY, (p.getStringSet(KEY, emptySet()) ?: emptySet()) + "$hhmm|$text").apply()
        schedule(ctx, hhmm, text)
        return true
    }

    fun cancelAll(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        WorkManager.getInstance(ctx).cancelAllWorkByTag(TAG)
    }

    /** Chamar ao iniciar o serviço (recupera rotinas após reboot). */
    fun rescheduleAll(ctx: Context) {
        val set = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()) ?: return
        set.forEach { it.split("|", limit = 2).takeIf { p -> p.size == 2 }?.let { p -> schedule(ctx, p[0], p[1]) } }
    }

    private fun parse(hhmm: String): Pair<Int, Int>? {
        val m = Regex("(\\d{1,2}):(\\d{2})").find(hhmm) ?: return null
        val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h to min else null
    }

    fun schedule(ctx: Context, hhmm: String, text: String) {
        val (h, m) = parse(hhmm) ?: return
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val req = OneTimeWorkRequestBuilder<RoutineWorker>()
            .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("hhmm" to hhmm, "text" to text))
            .addTag(TAG).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("routine:$hhmm:${text.hashCode()}", ExistingWorkPolicy.REPLACE, req)
    }
}

class RoutineWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val hhmm = inputData.getString("hhmm") ?: return Result.failure()
        val text = inputData.getString("text") ?: return Result.failure()
        val brief = text == "__BRIEF__"
        val shown = if (brief) DailyBrief.build(ctx) else text

        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("giselle_routines", "Giselle (rotinas)", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(
            text.hashCode(),
            NotificationCompat.Builder(ctx, "giselle_routines").setContentTitle("Giselle").setContentText(shown)
                .setSmallIcon(android.R.drawable.ic_popup_reminder).setAutoCancel(true).build()
        )
        // Executa também como comando do agente (pode ser bloqueado em background — a notificação já avisou).
        runCatching {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, JarvisForegroundService::class.java).apply {
                    action = if (brief) JarvisForegroundService.ACTION_SPEAK else JarvisForegroundService.ACTION_USER_COMMAND
                    putExtra(JarvisForegroundService.EXTRA_COMMAND, shown)
                }
            )
        }
        RoutineScheduler.schedule(ctx, hhmm, text) // reagenda para amanhã
        return Result.success()
    }
}
