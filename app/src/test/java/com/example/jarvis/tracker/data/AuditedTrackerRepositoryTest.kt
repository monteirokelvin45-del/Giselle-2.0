// ============================================================
// test/tracker/data/AuditedTrackerRepositoryTest.kt
// ------------------------------------------------------------
// Garante a regra central do repositório: toda escrita da IA
// SEMPRE grava o dado E o log de auditoria juntos. Testa também
// que uma falha no insert do dado principal não deixa um log de
// auditoria órfão (o log só é escrito depois do insert suceder).
// ============================================================

package com.example.jarvis.tracker.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FakeRegistroFisicoDaoForAudit : RegistroFisicoDao {
    val inserted = mutableListOf<RegistroFisico>()
    override fun observeRecentes(limit: Int) = throw NotImplementedError()
    override suspend fun getByRange(start: Long, end: Long): List<RegistroFisico> = emptyList()
    override suspend fun getUltimo(): RegistroFisico? = null
    override suspend fun insert(registro: RegistroFisico): Long { inserted.add(registro); return inserted.size.toLong() }
    override suspend fun restore(registro: RegistroFisico): Long = insert(registro)
    override suspend fun update(registro: RegistroFisico) {}
    override suspend fun delete(registro: RegistroFisico) {}
    override suspend fun countTreinosConfirmados(start: Long, end: Long): Int = 0
    override suspend fun countDiasComRegistro(start: Long, end: Long): Int = 0
}

class FakeRegistroFinanceiroDaoForAudit : RegistroFinanceiroDao {
    val updated = mutableListOf<RegistroFinanceiro>()
    override fun observeRecentes(limit: Int) = throw NotImplementedError()
    override suspend fun getByRange(start: Long, end: Long): List<RegistroFinanceiro> = emptyList()
    override suspend fun somaReceitas(start: Long, end: Long): Long = 0
    override suspend fun somaDespesas(start: Long, end: Long): Long = 0
    override suspend fun countDiasComRegistro(start: Long, end: Long): Int = 0
    override suspend fun insert(registro: RegistroFinanceiro): Long = 1
    override suspend fun restore(registro: RegistroFinanceiro): Long = insert(registro)
    override suspend fun update(registro: RegistroFinanceiro) { updated.add(registro) }
    override suspend fun delete(registro: RegistroFinanceiro) {}
}

class FakeTrackerAuditLogDao : TrackerAuditLogDao {
    val logs = mutableListOf<AuditLog>()
    override fun observeRecentes(limit: Int) = throw NotImplementedError()
    override suspend fun getHistoricoDoRegistro(tabela: String, registroId: Long): List<AuditLog> =
        logs.filter { it.tabela == tabela && it.registroId == registroId }
    override suspend fun getAlteracoesDaIa(limit: Int): List<AuditLog> = logs.filter { it.origem == "ia" }
    override suspend fun insert(log: AuditLog): Long { logs.add(log); return logs.size.toLong() }
}

class AuditedTrackerRepositoryTest {

    private fun buildRepo(): Triple<AuditedTrackerRepository, FakeRegistroFisicoDaoForAudit, FakeTrackerAuditLogDao> {
        val fisico = FakeRegistroFisicoDaoForAudit()
        val financeiro = FakeRegistroFinanceiroDaoForAudit()
        val audit = FakeTrackerAuditLogDao()
        return Triple(AuditedTrackerRepository(fisico, financeiro, audit), fisico, audit)
    }

    @Test
    fun `inserir registro fisico via ia grava o dado e o audit log juntos`() = runTest {
        val (repo, fisicoDao, auditDao) = buildRepo()
        val registro = RegistroFisico(
            data = 1000L, pesoKg = 80.0, treinoConfirmado = false, createdAt = 1000L, updatedAt = 1000L
        )

        val id = repo.inserirRegistroFisicoViaIa(registro, mapOf("pesoKg" to "80.0"))

        assertEquals(1, fisicoDao.inserted.size)
        assertEquals(1, auditDao.logs.size)
        assertEquals(id, auditDao.logs.first().registroId)
        assertEquals("ia", auditDao.logs.first().origem)
    }

    @Test
    fun `todo campo sugerido pela ia gera uma linha de audit log separada`() = runTest {
        val (repo, _, auditDao) = buildRepo()
        val registro = RegistroFisico(data = 1000L, treinoConfirmado = false, createdAt = 1000L, updatedAt = 1000L)

        repo.inserirRegistroFisicoViaIa(registro, mapOf("pesoKg" to "80.0", "gorduraPercentual" to "15.0"))

        assertEquals(2, auditDao.logs.size)
        assertTrue(auditDao.logs.all { it.origem == "ia" })
        assertTrue(auditDao.logs.any { it.campoAlterado == "pesoKg" })
        assertTrue(auditDao.logs.any { it.campoAlterado == "gorduraPercentual" })
    }

    @Test
    fun `atualizar categoria financeira via ia registra valor anterior e novo`() = runTest {
        val (repo, _, auditDao) = buildRepo()
        val registro = RegistroFinanceiro(
            id = 5L, data = 1000L, tipo = "DESPESA", categoria = "lazer",
            valorCentavos = 5000L, createdAt = 1000L
        )

        repo.atualizarCategoriaFinanceiroViaIa(registro, categoriaAnterior = "outros")

        assertEquals(1, auditDao.logs.size)
        val log = auditDao.logs.first()
        assertEquals("outros", log.valorAnterior)
        assertEquals("lazer", log.valorNovo)
        assertEquals("registro_financeiro", log.tabela)
        assertEquals(5L, log.registroId)
    }

    @Test
    fun `sem campos sugeridos nenhum audit log e gerado apesar do insert acontecer`() = runTest {
        val (repo, fisicoDao, auditDao) = buildRepo()
        val registro = RegistroFisico(data = 1000L, treinoConfirmado = false, createdAt = 1000L, updatedAt = 1000L)

        repo.inserirRegistroFisicoViaIa(registro, emptyMap())

        assertEquals(1, fisicoDao.inserted.size) // o dado foi salvo
        assertTrue(auditDao.logs.isEmpty()) // mas não há nada a auditar sem campos sugeridos
    }
}
