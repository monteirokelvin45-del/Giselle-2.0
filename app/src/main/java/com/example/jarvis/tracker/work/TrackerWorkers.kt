// ============================================================
// tracker/work/TrackerWorkers.kt
// ------------------------------------------------------------
// Item 18 — versão enxuta: Worker diário (gera insight se houver
// dado suficiente — o TrackerSafetyGate já decide isso dentro de
// generateInsight()) e Worker semanal (backup automático).
// Agendamento fica no chamador (MainActivity), este arquivo só
// define o trabalho em si.
// ============================================================

package com.example.jarvis.tracker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.jarvis.BuildConfig
import com.example.jarvis.core.CircuitBreaker
import com.example.jarvis.core.RateLimiter
import com.example.jarvis.llm.TrackerInsightClient
import com.example.jarvis.llm.ProxyAuth
import com.example.jarvis.tracker.agent.TrackerOrchestrator
import com.example.jarvis.tracker.data.BackupManager
import com.example.jarvis.tracker.data.TrackerDatabase

class DailyInsightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = TrackerDatabase.getInstance(applicationContext)
        val orchestrator = TrackerOrchestrator(
            claude = TrackerInsightClient(
                proxyBaseUrl = BuildConfig.PROXY_BASE_URL.ifBlank { null },
                proxySharedSecret = BuildConfig.PROXY_SHARED_SECRET.ifBlank { null },
                proxyClientId = ProxyAuth.clientId(applicationContext)
            ),
            configPerfilDao = db.configPerfilDao(), metasDao = db.metasDao(),
            registroFisicoDao = db.registroFisicoDao(), registroMentalDao = db.registroMentalDao(),
            registroEstudoDao = db.registroEstudoDao(), registroFinanceiroDao = db.registroFinanceiroDao(),
            aiEventDao = db.aiEventDao(),
            rateLimiter = RateLimiter(maxCallsPerWindow = 3, windowMs = 60_000L),
            circuitBreaker = CircuitBreaker(failureThreshold = 3, openDurationMs = 300_000L),
            onInsightGenerated = {}, onCircuitOpen = {}
        )
        return if (orchestrator.generateInsightAndAwait(origem = "workmanager_diario")) Result.success()
        else Result.retry()
    }
}

class WeeklyBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val db = TrackerDatabase.getInstance(applicationContext)
        BackupManager(applicationContext, db).exportarBackup(tipo = "automatico")
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
