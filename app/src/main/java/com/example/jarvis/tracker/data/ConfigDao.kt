// ============================================================
// tracker/data/ConfigDao.kt
// ------------------------------------------------------------
// DAOs das tabelas single-row (id fixo = 0, sempre uma linha só,
// modelo local single-user). Cada upsert aqui é INTENCIONALMENTE
// um REPLACE — não é acidente de configuração do Room, é a forma
// correta de "a linha 0 é a atual, ponto final". Isolado num
// arquivo próprio pra essa intenção ficar clara e auditável.
// ============================================================

package com.example.jarvis.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigPerfilDao {
    @Query("SELECT * FROM config_perfil WHERE id = 0")
    fun observe(): Flow<ConfigPerfil?>

    @Query("SELECT * FROM config_perfil WHERE id = 0")
    suspend fun get(): ConfigPerfil?

    // REPLACE proposital: só existe a linha 0, salvar = substituir o estado atual.
    // USAR upsert() SÓ NO ONBOARDING, que sempre monta o objeto completo.
    // Qualquer edição parcial futura (ex: tela "editar perfil") DEVE usar os
    // métodos de UPDATE nomeado abaixo, nunca upsert() — REPLACE do objeto
    // inteiro apaga silenciosamente campos que não vieram naquela chamada.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(perfil: ConfigPerfil)

    @Query("UPDATE config_perfil SET nome = :nome, updatedAt = :updatedAt WHERE id = 0")
    suspend fun updateNome(nome: String?, updatedAt: Long)

    @Query("UPDATE config_perfil SET pesoInicialKg = :peso, updatedAt = :updatedAt WHERE id = 0")
    suspend fun updatePeso(peso: Double?, updatedAt: Long)

    @Query("UPDATE config_perfil SET objetivoPrincipal = :objetivo, updatedAt = :updatedAt WHERE id = 0")
    suspend fun updateObjetivo(objetivo: String?, updatedAt: Long)

    @Query("SELECT status FROM config_perfil WHERE id = 0")
    suspend fun getStatus(): String?
}

@Dao
interface OnboardingStateDao {
    @Query("SELECT * FROM onboarding_state WHERE id = 0")
    fun observe(): Flow<OnboardingState?>

    @Query("SELECT * FROM onboarding_state WHERE id = 0")
    suspend fun get(): OnboardingState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: OnboardingState)

    // Limpa o estado de onboarding após a confirmação final — não faz
    // sentido reter "progresso em andamento" de um onboarding já concluído.
    @Query("DELETE FROM onboarding_state WHERE id = 0")
    suspend fun clear()
}

@Dao
interface MetasDao {
    @Query("SELECT * FROM metas WHERE id = 0")
    fun observe(): Flow<Metas?>

    @Query("SELECT * FROM metas WHERE id = 0")
    suspend fun get(): Metas?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metas: Metas)
}
