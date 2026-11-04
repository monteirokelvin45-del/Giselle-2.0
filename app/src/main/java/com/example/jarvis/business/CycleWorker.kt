// business/CycleWorker.kt — acorda o motor. Devolve sempre success: sem tempestade de tentativas
// e sem chamadas extra ao modelo quando algo falha. Os erros ficam no registo do ciclo.
package com.example.jarvis.business

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CycleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            GiselleBusiness.cycleIfDue(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.success()
        }
    }
}
