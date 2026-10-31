// ============================================================
// selfupdate/SelfDiagnosisWorker.kt
// ------------------------------------------------------------
// Antes: SelfDiagnosisAgent.diagnosticarPendentes() só rodava se
// algo chamasse manualmente — nada disparava sozinho. Este Worker
// fecha isso, mesmo padrão de TrackerWorkers.kt (CoroutineWorker,
// agendamento fica no chamador). Roda 1x/dia — diagnóstico não é
// urgente, e cada chamada custa uma requisição de API por erro
// pendente, então rodar com mais frequência só queimaria tokens
// sem ganho real de utilidade.
// ============================================================

package com.example.jarvis.selfupdate

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.jarvis.BuildConfig
import com.example.jarvis.llm.ClaudeClient
import com.example.jarvis.llm.ProxyAuth
import kotlinx.coroutines.flow.first

class SelfDiagnosisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val db = SelfUpdateDatabase.getInstance(applicationContext)
        val agent = SelfDiagnosisAgent(
            errorLogDao = db.errorLogDao(),
            patchDao = db.patchSuggestionDao(),
            llm = ClaudeDiagnosisLlmPort(
                ClaudeClient(
                    proxyBaseUrl = BuildConfig.PROXY_BASE_URL.ifBlank { null },
                    proxySharedSecret = BuildConfig.PROXY_SHARED_SECRET.ifBlank { null },
                    proxyClientId = ProxyAuth.clientId(applicationContext)
                )
            )
        )
        val pendentes = db.errorLogDao().pendentesDeDiagnostico().first()
        if (pendentes.isNotEmpty()) agent.diagnosticarPendentes(pendentes)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
