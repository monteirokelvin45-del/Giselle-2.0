// ============================================================
// test/tracker/onboarding/OnboardingViewModelTest.kt
// ------------------------------------------------------------
// Cobre a lógica de negócio pura do onboarding, sem Android:
// validação por etapa, navegação, persistência de progresso a
// cada mudança, e materialização final em ConfigPerfil/Metas/
// PlanoTreino só na confirmação.
// ============================================================

package com.example.jarvis.tracker.onboarding

import com.example.jarvis.tracker.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeOnboardingStateDao : OnboardingStateDao {
    var stored: OnboardingState? = null
    override fun observe(): Flow<OnboardingState?> = MutableStateFlow(stored)
    override suspend fun get(): OnboardingState? = stored
    override suspend fun upsert(state: OnboardingState) { stored = state }
    override suspend fun clear() { stored = null }
}

class FakeConfigPerfilDao : ConfigPerfilDao {
    var stored: ConfigPerfil? = null
    override fun observe(): Flow<ConfigPerfil?> = MutableStateFlow(stored)
    override suspend fun get(): ConfigPerfil? = stored
    override suspend fun upsert(perfil: ConfigPerfil) { stored = perfil }
    override suspend fun updateNome(nome: String?, updatedAt: Long) {
        stored = stored?.copy(nome = nome, updatedAt = updatedAt)
    }
    override suspend fun updatePeso(peso: Double?, updatedAt: Long) {
        stored = stored?.copy(pesoInicialKg = peso, updatedAt = updatedAt)
    }
    override suspend fun updateObjetivo(objetivo: String?, updatedAt: Long) {
        stored = stored?.copy(objetivoPrincipal = objetivo, updatedAt = updatedAt)
    }
    override suspend fun getStatus(): String? = stored?.status
}

class FakeMetasDao : MetasDao {
    var stored: Metas? = null
    override fun observe(): Flow<Metas?> = MutableStateFlow(stored)
    override suspend fun get(): Metas? = stored
    override suspend fun upsert(metas: Metas) { stored = metas }
}

class FakePlanoTreinoDao : PlanoTreinoDao {
    val inserted = mutableListOf<PlanoTreino>()
    override fun observeAtivos(): Flow<List<PlanoTreino>> = MutableStateFlow(inserted.filter { it.ativo })
    override suspend fun getAll(): List<PlanoTreino> = inserted
    override suspend fun insert(plano: PlanoTreino): Long { inserted.add(plano); return inserted.size.toLong() }
    override suspend fun insertAll(planos: List<PlanoTreino>) { inserted.addAll(planos) }
    override suspend fun update(plano: PlanoTreino) {}
    override suspend fun desativar(id: Long) {}
    override suspend fun delete(plano: PlanoTreino) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private lateinit var onboardingStateDao: FakeOnboardingStateDao
    private lateinit var configPerfilDao: FakeConfigPerfilDao
    private lateinit var metasDao: FakeMetasDao
    private lateinit var planoTreinoDao: FakePlanoTreinoDao
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        onboardingStateDao = FakeOnboardingStateDao()
        configPerfilDao = FakeConfigPerfilDao()
        metasDao = FakeMetasDao()
        planoTreinoDao = FakePlanoTreinoDao()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun buildViewModel() = OnboardingViewModel(onboardingStateDao, configPerfilDao, metasDao, planoTreinoDao)

    @Test
    fun `nao avanca da etapa perfil sem nome e idade`() = runTest {
        val vm = buildViewModel()
        assertFalse(vm.canAdvance()) // perfil vazio

        vm.updateAnswers { it.copy(nome = "Ana") }
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.canAdvance()) // falta idade

        vm.updateAnswers { it.copy(idade = 28) }
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.canAdvance())
    }

    @Test
    fun `avancar etapa so funciona quando a etapa atual e valida`() = runTest {
        val vm = buildViewModel()
        vm.goToNextStep() // não deveria avançar, perfil inválido
        assertEquals(OnboardingStep.PERFIL, vm.currentStep.value)

        vm.updateAnswers { it.copy(nome = "Ana", idade = 28) }
        dispatcher.scheduler.advanceUntilIdle()
        vm.goToNextStep()
        assertEquals(OnboardingStep.TREINO, vm.currentStep.value)
    }

    @Test
    fun `voltar etapa nao passa do inicio`() = runTest {
        val vm = buildViewModel()
        vm.goToPreviousStep()
        assertEquals(OnboardingStep.PERFIL, vm.currentStep.value)
    }

    @Test
    fun `cada mudanca de resposta persiste progresso como em_andamento`() = runTest {
        val vm = buildViewModel()
        vm.updateAnswers { it.copy(nome = "Ana") }
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(onboardingStateDao.stored)
        assertEquals("em_andamento", onboardingStateDao.stored?.status)
        assertTrue(onboardingStateDao.stored!!.respostasJson.contains("Ana"))
    }

    @Test
    fun `confirmar onboarding materializa perfil com status confirmado`() = runTest {
        val vm = buildViewModel()
        vm.updateAnswers { it.copy(nome = "Ana", idade = 28, objetivoPrincipal = "hipertrofia") }
        dispatcher.scheduler.advanceUntilIdle()

        vm.confirmOnboarding()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("confirmado", configPerfilDao.stored?.status)
        assertEquals("Ana", configPerfilDao.stored?.nome)
        assertTrue(vm.isComplete.value)
    }

    @Test
    fun `confirmar onboarding limpa o estado de progresso`() = runTest {
        val vm = buildViewModel()
        vm.updateAnswers { it.copy(nome = "Ana", idade = 28) }
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(onboardingStateDao.stored) // progresso existe antes de confirmar

        vm.confirmOnboarding()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(onboardingStateDao.stored) // clear() foi chamado
    }

    @Test
    fun `confirmar onboarding cria um PlanoTreino por dia selecionado`() = runTest {
        val vm = buildViewModel()
        vm.updateAnswers {
            it.copy(
                nome = "Ana", idade = 28,
                diasTreinoSemana = listOf(2, 4),
                grupoMuscularPorDia = mapOf(2 to "peito", 4 to "costas")
            )
        }
        dispatcher.scheduler.advanceUntilIdle()

        vm.confirmOnboarding()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, planoTreinoDao.inserted.size)
        assertTrue(planoTreinoDao.inserted.any { it.diaSemana == 2 && it.grupoMuscular == "peito" })
    }

    @Test
    fun `sem dias de treino selecionados nao cria nenhum PlanoTreino`() = runTest {
        val vm = buildViewModel()
        vm.updateAnswers { it.copy(nome = "Ana", idade = 28) }
        dispatcher.scheduler.advanceUntilIdle()

        vm.confirmOnboarding()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(planoTreinoDao.inserted.isEmpty())
    }
}
