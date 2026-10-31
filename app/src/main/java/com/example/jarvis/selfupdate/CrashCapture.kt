// ============================================================
// selfupdate/CrashCapture.kt
// ------------------------------------------------------------
// Captura toda exceção não tratada do app (crash) e qualquer
// erro logado explicitamente (ex: falha de parsing, resposta
// inesperada da API) — sem nunca corrigir nada sozinho aqui.
// Este arquivo só OBSERVA e GRAVA. A decisão de diagnosticar
// e sugerir correção vive em SelfDiagnosisAgent.kt, separado
// de propósito: quem detecta o erro não é quem decide o que
// fazer com ele.
// ============================================================

package com.example.jarvis.selfupdate

import android.content.Context
import android.content.ContentValues
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "error_log")
data class ErrorLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val tipo: String,              // "crash_fatal" | "erro_capturado"
    val threadName: String,
    val exceptionClass: String,
    val message: String?,
    val stackTrace: String,
    val telaAtual: String?,        // qual tela/módulo estava ativo, se souber
    val versaoApp: String,
    val diagnosticado: Boolean = false
)

@Dao
interface ErrorLogDao {
    @Insert
    suspend fun insert(error: ErrorLogEntity): Long

    @Query("SELECT * FROM error_log WHERE diagnosticado = 0 ORDER BY timestamp DESC")
    fun pendentesDeDiagnostico(): Flow<List<ErrorLogEntity>>

    @Query("SELECT * FROM error_log ORDER BY timestamp DESC LIMIT :limite")
    fun recentes(limite: Int = 50): Flow<List<ErrorLogEntity>>

    @Query("UPDATE error_log SET diagnosticado = 1 WHERE id = :id")
    suspend fun marcarDiagnosticado(id: Long)
}

/**
 * Registra-se como handler global de crash. Não impede o crash
 * (isso seria mascarar o problema) — só garante que a informação
 * sobrevive ao restart do app pra poder ser diagnosticada depois.
 */
class CrashCaptureHandler(
    private val context: Context,
    private val database: SelfUpdateDatabase,
    private val versaoApp: String,
    private val original: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val values = ContentValues().apply {
                put("timestamp", System.currentTimeMillis())
                put("tipo", "crash_fatal")
                put("threadName", thread.name)
                put("exceptionClass", throwable::class.qualifiedName ?: "Desconhecida")
                put("message", throwable.message)
                put("stackTrace", throwable.stackTraceToString().take(48_000))
                putNull("telaAtual")
                put("versaoApp", versaoApp)
                put("diagnosticado", 0)
            }
            database.openHelper.writableDatabase.insert(
                "error_log", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values
            )
        } catch (_: Throwable) {
            // se a própria captura falhar, não podemos deixar isso
            // impedir o crash original de seguir seu curso normal
        }
        original?.uncaughtException(thread, throwable)
    }

    companion object {
        fun install(context: Context, database: SelfUpdateDatabase, versaoApp: String) {
            val original = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(
                CrashCaptureHandler(context, database, versaoApp, original)
            )
        }
    }
}

/**
 * Uso fora de crash fatal: erros capturados via try/catch que você
 * julga relevantes registrar (ex: parsing de resposta da IA falhou,
 * uma chamada de rede retornou formato inesperado). Chame explicitamente
 * onde fizer sentido — isso não substitui tratamento de erro normal,
 * só alimenta o diagnóstico posterior.
 */
suspend fun registrarErroCapturado(
    dao: ErrorLogDao,
    throwable: Throwable,
    tela: String,
    versaoApp: String
) {
    dao.insert(
        ErrorLogEntity(
            timestamp = System.currentTimeMillis(),
            tipo = "erro_capturado",
            threadName = Thread.currentThread().name,
            exceptionClass = throwable::class.qualifiedName ?: "Desconhecida",
            message = throwable.message,
            stackTrace = throwable.stackTraceToString(),
            telaAtual = tela,
            versaoApp = versaoApp
        )
    )
}
