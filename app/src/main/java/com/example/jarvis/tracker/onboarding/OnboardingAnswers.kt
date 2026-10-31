// ============================================================
// tracker/onboarding/OnboardingAnswers.kt
// ------------------------------------------------------------
// Estrutura de todas as respostas coletadas durante o onboarding,
// serializada para JSON e guardada em OnboardingState.respostasJson
// a cada etapa — é isso que permite retomar de onde parou se o
// usuário fechar o app no meio (o campo já existia no schema
// original, este arquivo é o que finalmente o usa de verdade).
// ============================================================

package com.example.jarvis.tracker.onboarding

import com.example.jarvis.tracker.data.OnboardingStep
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class OnboardingAnswers(
    // Etapa 1: Perfil básico
    val nome: String? = null,
    val idade: Int? = null,
    val alturaCm: Int? = null,
    val pesoInicialKg: Double? = null,
    val objetivoPrincipal: String? = null,
    val nivelExperienciaTreino: String? = null,

    // Etapa 2: Rotina de treino (vira PlanoTreino na confirmação)
    val diasTreinoSemana: List<Int> = emptyList(), // 1-7
    val grupoMuscularPorDia: Map<Int, String> = emptyMap(),

    // Etapa 3: Sono e hábitos
    val horaDormirHabitual: String? = null,
    val horaAcordarHabitual: String? = null,

    // Etapa 4: Estudos e carreira
    val areaEstudoPrincipal: String? = null,
    val horasEstudoSemanaAtual: Int? = null,

    // Etapa 5: Finanças
    val moeda: String = "MZN",
    val rendaMensalAtual: Double? = null,

    // Etapa 6: Metas 10/10
    val pesoIdealKg: Double? = null,
    val gorduraIdealPercentual: Double? = null,
    val rendaMensalAlvo: Double? = null,
    val horasEstudoSemanaAlvo: Int? = null,
    val reservaFinanceiraAlvo: Double? = null,
    val visao3Meses: String? = null,
    val visao12Meses: String? = null
) {
    fun toJson(): String = Json.encodeToString(serializer(), this)

    companion object {
        fun fromJson(json: String): OnboardingAnswers = try {
            Json.decodeFromString(serializer(), json)
        } catch (e: Exception) {
            OnboardingAnswers() // JSON corrompido ou de uma versão antiga -> começa do zero
        }
    }
}

/**
 * Validação mínima por etapa — não deixa avançar com o essencial
 * vazio, mas é deliberadamente permissivo com o resto (a pessoa
 * pode não saber a % de gordura, por exemplo, e não deveria travar
 * o onboarding por isso).
 */
object OnboardingValidation {
    fun isStepValid(step: OnboardingStep, answers: OnboardingAnswers): Boolean = when (step) {
        OnboardingStep.PERFIL -> !answers.nome.isNullOrBlank() && answers.idade != null && answers.idade > 0
        OnboardingStep.TREINO -> true // treino é opcional — nem todo mundo treina
        OnboardingStep.SONO_HABITOS -> true
        OnboardingStep.ESTUDOS_CARREIRA -> true
        OnboardingStep.FINANCAS -> answers.moeda.isNotBlank()
        OnboardingStep.METAS -> true
        OnboardingStep.CONFIRMACAO -> true
    }
}
