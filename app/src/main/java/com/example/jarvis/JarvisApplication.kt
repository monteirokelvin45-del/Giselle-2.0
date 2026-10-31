// ============================================================
// JarvisApplication.kt
// ------------------------------------------------------------
// Instala captura de crash e agenda os Workers periódicos do
// app — nenhum dos três (insight diário, backup semanal,
// autodiagnóstico diário) estava sendo agendado até agora, só
// existiam as classes Worker sem ninguém chamar enqueue.
// ============================================================

package com.example.jarvis

import android.app.Application
import androidx.work.*
import com.example.jarvis.selfupdate.CrashCaptureHandler
import com.example.jarvis.selfupdate.SelfDiagnosisWorker
import com.example.jarvis.selfupdate.SelfUpdateDatabase
import com.example.jarvis.tracker.work.DailyInsightWorker
import com.example.jarvis.tracker.work.WeeklyBackupWorker
import java.util.concurrent.TimeUnit

class JarvisApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Atualizações: deteta arranques falhados da config remota e agenda a verificação periódica.
        com.example.jarvis.updates.GiselleUpdater(this).onAppStart()
        com.example.jarvis.updates.UpdateWorker.schedule(this)

        val db = SelfUpdateDatabase.getInstance(this)
        CrashCaptureHandler.install(
            context = this, database = db, versaoApp = BuildConfig.VERSION_NAME
        )

        agendarWorkersPeriodicos()
    }

    private fun agendarWorkersPeriodicos() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // todos os 3 dependem de API ou I/O — sem rede, nem tenta
            .build()

        val wm = WorkManager.getInstance(this)

        wm.enqueueUniquePeriodicWork(
            "daily_insight", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyInsightWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints).build()
        )
        wm.enqueueUniquePeriodicWork(
            "weekly_backup", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WeeklyBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints).build()
        )
        wm.enqueueUniquePeriodicWork(
            "self_diagnosis", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SelfDiagnosisWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints).build()
        )
    }
}
