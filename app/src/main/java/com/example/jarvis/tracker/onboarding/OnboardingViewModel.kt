// ============================================================
// tracker/onboarding/OnboardingViewModel.kt
// ------------------------------------------------------------
// Coordena o fluxo de 7 etapas. Duas responsabilidades que não
// podem se confundir:
//
// 1. A cada etapa, salva o progresso em OnboardingState (status
//    "em_andamento") — isso é o rascunho, pode ser incompleto,
//    existe só para retomar de onde parou.
//
// 2. SÓ na etapa de confirmação final, materializa os dados em
//    ConfigPerfil (status "confirmado"), Metas e PlanoTreino —
//    e só a partir desse momento eles contam para o resto do
//    app (TrackerOrchestrator, dashboard, KPIs). Antes disso,
//    ConfigPerfil.get() continua retornando null, como já era
//    tratado em TrackerOrchestrator.buildContext().
// ============================================================

package com.example.jarvis.tracker.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jarvis.tracker.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val onboardingStateDao: OnboardingStateDao,
    private val configPerfilDao: ConfigPerfilDao,
    private val metasDao: MetasDao,
    private val planoTreinoDao: PlanoTreinoDao
) : ViewModel() {

    private val _currentStep = MutableStateFlow(OnboardingStep.PERFIL)
    val currentStep: StateFlow<OnboardingStep> = _currentStep.asStateFlow()

    private val _answers = MutableStateFlow(OnboardingAnswers())
    val answers: StateFlow<OnboardingAnswers> = _answers.asStateFlow()

    private val _isComplete = MutableStateFlow(false)
    val isComplete: StateFlow<Boolean> = _isComplete.asStateFlow()

    init {
        // Retoma progresso salvo, se existir — é o que faz o campo
        // respostasJson do schema original finalmente ter propósito.
        viewModelScope.launch {
            val existing = onboardingStateDao.get()
            if (existing != null && existing.status == "em_andamento") {
                _answers.value = OnboardingAnswers.fromJson(existing.respostasJson)
                _currentStep.value = OnboardingStep.entries.find { it.numero == existing.etapaAtual }
                    ?: OnboardingStep.PERFIL
            }
        }
    }

    fun updateAnswers(transform: (OnboardingAnswers) -> OnboardingAnswers) {
        _answers.value = transform(_answers.value)
        persistProgress()
    }

    fun canAdvance(): Boolean = OnboardingValidation.isStepValid(_currentStep.value, _answers.value)

    fun goToNextStep() {
        if (!canAdvance()) return
        val steps = OnboardingStep.entries
        val currentIndex = steps.indexOf(_currentStep.value)
        if (currentIndex < steps.lastIndex) {
            _currentStep.value = steps[currentIndex + 1]
            persistProgress()
        }
    }

    fun goToPreviousStep() {
        val steps = OnboardingStep.entries
        val currentIndex = steps.indexOf(_currentStep.value)
        if (currentIndex > 0) {
            _currentStep.value = steps[currentIndex - 1]
            persistProgress()
        }
    }

    /**
     * Chamado só na etapa CONFIRMACAO. Materializa tudo de uma vez:
     * ConfigPerfil com status="confirmado", Metas, e um PlanoTreino
     * por dia selecionado. Depois disso, apaga o OnboardingState —
     * não faz sentido reter "progresso em andamento" de algo já
     * concluído.
     */
    fun confirmOnboarding() {
        viewModelScope.launch {
            val a = _answers.value
            val now = System.currentTimeMillis()

            configPerfilDao.upsert(
                ConfigPerfil(
                    nome = a.nome,
                    idade = a.idade,
                    alturaCm = a.alturaCm,
                    pesoInicialKg = a.pesoInicialKg,
                    objetivoPrincipal = a.objetivoPrincipal,
                    nivelExperienciaTreino = a.nivelExperienciaTreino,
                    moeda = a.moeda,
                    status = "confirmado",
                    createdAt = now,
                    updatedAt = now
                )
            )

            metasDao.upsert(
                Metas(
                    pesoIdealKg = a.pesoIdealKg,
                    gorduraIdealPercentual = a.gorduraIdealPercentual,
                    rendaMensalAlvo = a.rendaMensalAlvo,
                    horasEstudoSemanaAlvo = a.horasEstudoSemanaAlvo,
                    reservaFinanceiraAlvo = a.reservaFinanceiraAlvo,
                    visao3Meses = a.visao3Meses,
                    visao12Meses = a.visao12Meses,
                    updatedAt = now
                )
            )

            if (a.diasTreinoSemana.isNotEmpty()) {
                val planos = a.diasTreinoSemana.map { dia ->
                    PlanoTreino(
                        diaSemana = dia,
                        grupoMuscular = a.grupoMuscularPorDia[dia] ?: "não especificado",
                        ativo = true
                    )
                }
                planoTreinoDao.insertAll(planos)
            }

            onboardingStateDao.clear()
            _isComplete.value = true
        }
    }

    private fun persistProgress() {
        viewModelScope.launch {
            onboardingStateDao.upsert(
                OnboardingState(
                    etapaAtual = _currentStep.value.numero,
                    respostasJson = _answers.value.toJson(),
                    status = "em_andamento",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}
