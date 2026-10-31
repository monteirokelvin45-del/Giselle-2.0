// ============================================================
// core/AuditLog.kt
// ------------------------------------------------------------
// Log auditável, append-only, de TUDO que o Jarvis decidiu e
// executou. Diferente da MemoryStore (que guarda aprendizado
// resumido), isso é o registro bruto e completo — pra você
// conseguir responder "o que exatamente ele fez às 14:32?"
// com certeza, inclusive em ações que falharam ou foram
// rejeitadas. Nunca é editado, só inserido.
// ============================================================

package com.example.jarvis.core

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val app: String,
    val userCommand: String?,
    val actionType: String,
    val target: String?,
    val inputText: String?,
    val reasoning: String,
    val riskLevel: String,
    val requiredConfirmation: Boolean,
    val outcome: String,
    val errorMessage: String? = null
)

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(entry: AuditLogEntity)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 200): List<AuditLogEntity>

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun getByRange(start: Long, end: Long): List<AuditLogEntity>

    @Query("SELECT COUNT(*) FROM audit_log WHERE outcome = 'FAILED' AND timestamp > :since")
    suspend fun countFailuresSince(since: Long): Int

    // Auditoria = nunca deleta em uso normal. Isto existe só para
    // rotina explícita de retenção (ex: expurgar logs com +90 dias),
    // nunca chamado a partir de fluxo automático de "limpeza".
    @Query("DELETE FROM audit_log WHERE timestamp < :before")
    suspend fun purgeOlderThan(before: Long)
}

class AuditLog(private val dao: AuditLogDao) {

    suspend fun record(
        app: String,
        userCommand: String?,
        decision: AgentDecision,
        riskLevel: RiskLevel,
        outcome: ActionOutcome,
        errorMessage: String? = null
    ) {
        dao.insert(
            AuditLogEntity(
                timestamp = System.currentTimeMillis(),
                app = app,
                userCommand = userCommand,
                actionType = decision.actionType.name,
                target = decision.target,
                inputText = decision.inputText,
                reasoning = decision.reasoning,
                riskLevel = riskLevel.name,
                requiredConfirmation = decision.requiresConfirmation,
                outcome = outcome.name,
                errorMessage = errorMessage
            )
        )
    }

    suspend fun recent(limit: Int = 200) = dao.getRecent(limit)

    /** Usado pelo circuit breaker: quantas falhas nos últimos N ms. */
    suspend fun recentFailureCount(windowMs: Long): Int =
        dao.countFailuresSince(System.currentTimeMillis() - windowMs)
}
