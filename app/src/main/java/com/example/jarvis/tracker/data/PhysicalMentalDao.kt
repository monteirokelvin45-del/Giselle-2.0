// ============================================================
// tracker/data/PhysicalMentalDao.kt
// ------------------------------------------------------------
// Físico, mental e o protocolo Ayanokoji. Nota de design que
// se repete aqui: PlanoTreino é insert/update/delete normal
// (é um plano, edita livre); RegistroFisico e RegistroExercicio
// nunca têm update parcial silencioso de "treinoConfirmado" —
// isso é sempre um campo explícito na entidade que chega inteira.
// ============================================================

package com.example.jarvis.tracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanoTreinoDao {
    @Query("SELECT * FROM plano_treino WHERE ativo = 1 ORDER BY diaSemana ASC")
    fun observeAtivos(): Flow<List<PlanoTreino>>

    @Query("SELECT * FROM plano_treino ORDER BY diaSemana ASC")
    suspend fun getAll(): List<PlanoTreino>

    @Insert
    suspend fun insert(plano: PlanoTreino): Long

    @Insert
    suspend fun insertAll(planos: List<PlanoTreino>)

    @Update
    suspend fun update(plano: PlanoTreino)

    @Query("UPDATE plano_treino SET ativo = 0 WHERE id = :id")
    suspend fun desativar(id: Long)

    @Delete
    suspend fun delete(plano: PlanoTreino)
}

@Dao
interface RegistroFisicoDao {
    @Query("SELECT * FROM registro_fisico ORDER BY data DESC LIMIT :limit")
    fun observeRecentes(limit: Int = 60): Flow<List<RegistroFisico>>

    @Query("SELECT * FROM registro_fisico WHERE data BETWEEN :start AND :end ORDER BY data ASC")
    suspend fun getByRange(start: Long, end: Long): List<RegistroFisico>

    @Query("SELECT * FROM registro_fisico ORDER BY data DESC LIMIT 1")
    suspend fun getUltimo(): RegistroFisico?

    @Insert
    suspend fun insert(registro: RegistroFisico): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restore(registro: RegistroFisico): Long

    @Update
    suspend fun update(registro: RegistroFisico)

    @Delete
    suspend fun delete(registro: RegistroFisico)

    /** Usado pelo dashboard: quantos treinos CONFIRMADOS (não planejados) na janela. */
    @Query("SELECT COUNT(*) FROM registro_fisico WHERE treinoConfirmado = 1 AND data BETWEEN :start AND :end")
    suspend fun countTreinosConfirmados(start: Long, end: Long): Int

    /** Usado pelo TrackerSafetyGate: dias distintos com QUALQUER registro no período. */
    @Query("SELECT COUNT(DISTINCT data) FROM registro_fisico WHERE data BETWEEN :start AND :end")
    suspend fun countDiasComRegistro(start: Long, end: Long): Int
}

@Dao
interface RegistroExercicioDao {
    // FK real com CASCADE cuida da limpeza ao deletar o RegistroFisico pai;
    // esta query serve pra exibir os exercícios de um dia específico.
    @Query("SELECT * FROM registro_exercicio WHERE registroFisicoId = :registroFisicoId")
    suspend fun getByRegistroFisico(registroFisicoId: Long): List<RegistroExercicio>

    @Query("SELECT * FROM registro_exercicio ORDER BY registroFisicoId, id")
    suspend fun getAll(): List<RegistroExercicio>

    @Insert
    suspend fun insertAll(exercicios: List<RegistroExercicio>)

    @Insert
    suspend fun insert(exercicio: RegistroExercicio): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restore(exercicio: RegistroExercicio): Long

    @Delete
    suspend fun delete(exercicio: RegistroExercicio)
}

@Dao
interface RegistroMentalDao {
    @Query("SELECT * FROM registro_mental ORDER BY data DESC LIMIT :limit")
    fun observeRecentes(limit: Int = 60): Flow<List<RegistroMental>>

    @Query("SELECT * FROM registro_mental WHERE data BETWEEN :start AND :end ORDER BY data ASC")
    suspend fun getByRange(start: Long, end: Long): List<RegistroMental>

    @Query("SELECT * FROM registro_mental WHERE data = :data LIMIT 1")
    suspend fun getByData(data: Long): RegistroMental?

    /** Usado pelo TrackerSafetyGate: dias distintos com QUALQUER registro no período. */
    @Query("SELECT COUNT(DISTINCT data) FROM registro_mental WHERE data BETWEEN :start AND :end")
    suspend fun countDiasComRegistro(start: Long, end: Long): Int

    @Insert
    suspend fun insert(registro: RegistroMental): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restore(registro: RegistroMental): Long

    @Update
    suspend fun update(registro: RegistroMental)
}

@Dao
interface AyanokojiDao {
    @Query("SELECT * FROM ayanokoji_progresso WHERE id = 0")
    fun observeProgresso(): Flow<AyanokojiProgresso?>

    @Query("SELECT * FROM ayanokoji_progresso WHERE id = 0")
    suspend fun getProgresso(): AyanokojiProgresso?

    // REPLACE proposital — mesma lógica das tabelas single-row do ConfigDao.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgresso(progresso: AyanokojiProgresso)

    @Query("SELECT * FROM ayanokoji_registro_diario WHERE semana = :semana ORDER BY data ASC")
    suspend fun getRegistrosDaSemana(semana: Int): List<AyanokojiRegistroDiario>

    @Query("SELECT * FROM ayanokoji_registro_diario WHERE data = :data LIMIT 1")
    suspend fun getRegistroDoDia(data: Long): AyanokojiRegistroDiario?

    @Insert
    suspend fun insertRegistroDiario(registro: AyanokojiRegistroDiario): Long

    @Update
    suspend fun updateRegistroDiario(registro: AyanokojiRegistroDiario)

    // NOTA: nenhuma query aqui avança "semanaAtual" automaticamente.
    // Isso é decisão do usuário (ou de uma tela que ele confirma
    // explicitamente) — nunca um side-effect silencioso de gravar um registro.
}
