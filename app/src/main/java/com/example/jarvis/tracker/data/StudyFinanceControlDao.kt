// ============================================================
// tracker/data/StudyFinanceControlDao.kt
// ------------------------------------------------------------
// Estudos, leitura, finanças, e as tabelas de controle que dão
// rastreabilidade ao módulo inteiro (AiEvent, AuditLog, Backups).
// ============================================================

package com.example.jarvis.tracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RegistroEstudoDao {
    @Query("SELECT * FROM registro_estudo ORDER BY data DESC LIMIT :limit")
    fun observeRecentes(limit: Int = 60): Flow<List<RegistroEstudo>>

    @Query("SELECT * FROM registro_estudo WHERE data BETWEEN :start AND :end ORDER BY data ASC")
    suspend fun getByRange(start: Long, end: Long): List<RegistroEstudo>

    @Query("SELECT COALESCE(SUM(duracaoMinutos), 0) FROM registro_estudo WHERE data BETWEEN :start AND :end")
    suspend fun somaMinutosNaJanela(start: Long, end: Long): Int

    /** Usado pelo TrackerSafetyGate: dias distintos com QUALQUER registro no período. */
    @Query("SELECT COUNT(DISTINCT data) FROM registro_estudo WHERE data BETWEEN :start AND :end")
    suspend fun countDiasComRegistro(start: Long, end: Long): Int

    @Insert
    suspend fun insert(registro: RegistroEstudo): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restore(registro: RegistroEstudo): Long

    @Delete
    suspend fun delete(registro: RegistroEstudo)
}

@Dao
interface RegistroLeituraDao {
    @Query("SELECT * FROM registro_leitura ORDER BY data DESC LIMIT :limit")
    fun observeRecentes(limit: Int = 60): Flow<List<RegistroLeitura>>

    @Query("SELECT * FROM registro_leitura ORDER BY data ASC")
    suspend fun getAll(): List<RegistroLeitura>

    @Query("SELECT * FROM registro_leitura WHERE livro = :livro ORDER BY data DESC")
    suspend fun getByLivro(livro: String): List<RegistroLeitura>

    @Insert
    suspend fun insert(registro: RegistroLeitura): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restore(registro: RegistroLeitura): Long

    @Delete
    suspend fun delete(registro: RegistroLeitura)
}

@Dao
interface RegistroFinanceiroDao {
    @Query("SELECT * FROM registro_financeiro ORDER BY data DESC LIMIT :limit")
    fun observeRecentes(limit: Int = 100): Flow<List<RegistroFinanceiro>>

    @Query("SELECT * FROM registro_financeiro WHERE data BETWEEN :start AND :end ORDER BY data DESC")
    suspend fun getByRange(start: Long, end: Long): List<RegistroFinanceiro>

    // TRANSFERENCIA fica de fora de propósito — nunca deve contar como
    // receita ou despesa real nos KPIs, é só movimentação entre contas.
    @Query("""
        SELECT COALESCE(SUM(valorCentavos), 0) FROM registro_financeiro
        WHERE tipo = 'RECEITA' AND data BETWEEN :start AND :end
    """)
    suspend fun somaReceitas(start: Long, end: Long): Long

    @Query("""
        SELECT COALESCE(SUM(valorCentavos), 0) FROM registro_financeiro
        WHERE tipo = 'DESPESA' AND data BETWEEN :start AND :end
    """)
    suspend fun somaDespesas(start: Long, end: Long): Long

    /** Usado pelo TrackerSafetyGate: dias distintos com QUALQUER registro no período. */
    @Query("SELECT COUNT(DISTINCT data) FROM registro_financeiro WHERE data BETWEEN :start AND :end")
    suspend fun countDiasComRegistro(start: Long, end: Long): Int

    @Insert
    suspend fun insert(registro: RegistroFinanceiro): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restore(registro: RegistroFinanceiro): Long

    @Update
    suspend fun update(registro: RegistroFinanceiro)

    @Delete
    suspend fun delete(registro: RegistroFinanceiro)
}

// -----------------------------------------------------
// Tabelas de controle
// -----------------------------------------------------

@Dao
interface AiEventDao {
    @Query("SELECT * FROM ai_events ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentes(limit: Int = 100): Flow<List<AiEvent>>

    @Query("SELECT * FROM ai_events WHERE feedback IS NULL ORDER BY createdAt DESC")
    suspend fun getSemFeedback(): List<AiEvent>

    @Query("SELECT * FROM ai_events WHERE origem = :origem ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getByOrigem(origem: String, limit: Int = 50): List<AiEvent>

    @Insert
    suspend fun insert(event: AiEvent): Long

    /**
     * Único jeito de preencher feedback: nunca um insert que já nasce
     * com feedback preenchido. Reforça a regra de que aprendizado real
     * vem de uma reação explícita e posterior do usuário.
     */
    @Query("UPDATE ai_events SET feedback = :feedback WHERE id = :id")
    suspend fun registrarFeedback(id: Long, feedback: String)

    @Query("UPDATE ai_events SET resultadoPosterior = :resultado WHERE id = :id")
    suspend fun registrarResultadoPosterior(id: Long, resultado: String)
}

@Dao
interface TrackerAuditLogDao {
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentes(limit: Int = 200): Flow<List<AuditLog>>

    @Query("SELECT * FROM audit_log WHERE tabela = :tabela AND registroId = :registroId ORDER BY timestamp DESC")
    suspend fun getHistoricoDoRegistro(tabela: String, registroId: Long): List<AuditLog>

    @Query("SELECT * FROM audit_log WHERE origem = 'ia' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAlteracoesDaIa(limit: Int = 100): List<AuditLog>

    // Append-only por convenção de uso — o DAO não expõe update nem delete
    // de propósito. Se algo precisar ser corrigido, corrige-se com um novo
    // registro, nunca reescrevendo a história.
    @Insert
    suspend fun insert(log: AuditLog): Long
}

@Dao
interface BackupRegistroDao {
    @Query("SELECT * FROM backups ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BackupRegistro>>

    @Query("SELECT * FROM backups ORDER BY createdAt DESC LIMIT 1")
    suspend fun getUltimo(): BackupRegistro?

    @Insert
    suspend fun insert(backup: BackupRegistro): Long

    @Delete
    suspend fun delete(backup: BackupRegistro)
}
