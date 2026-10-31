// ============================================================
// tracker/onboarding/OnboardingScreen.kt
// ------------------------------------------------------------
// Host das 7 etapas: barra de progresso, botões Voltar/Avançar,
// delega o conteúdo de cada etapa para um composable próprio.
// ============================================================

package com.example.jarvis.tracker.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jarvis.tracker.data.OnboardingStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    factory: OnboardingViewModelFactory,
    onFinished: () -> Unit
) {
    val viewModel: OnboardingViewModel = viewModel(factory = factory)
    val step by viewModel.currentStep.collectAsStateWithLifecycle()
    val answers by viewModel.answers.collectAsStateWithLifecycle()
    val isComplete by viewModel.isComplete.collectAsStateWithLifecycle()

    if (isComplete) {
        onFinished()
        return
    }

    val steps = OnboardingStep.entries
    val stepIndex = steps.indexOf(step)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text(step.titulo) })
                LinearProgressIndicator(
                    progress = { (stepIndex + 1f) / steps.size },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (stepIndex > 0) {
                    OutlinedButton(onClick = { viewModel.goToPreviousStep() }) { Text("Voltar") }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (step == OnboardingStep.CONFIRMACAO) {
                    Button(onClick = { viewModel.confirmOnboarding() }) { Text("Confirmar e começar") }
                } else {
                    Button(
                        onClick = { viewModel.goToNextStep() },
                        enabled = viewModel.canAdvance()
                    ) { Text("Avançar") }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize()
        ) {
            when (step) {
                OnboardingStep.PERFIL -> PerfilStep(answers, viewModel::updateAnswers)
                OnboardingStep.TREINO -> TreinoStep(answers, viewModel::updateAnswers)
                OnboardingStep.SONO_HABITOS -> SonoHabitosStep(answers, viewModel::updateAnswers)
                OnboardingStep.ESTUDOS_CARREIRA -> EstudosCarreiraStep(answers, viewModel::updateAnswers)
                OnboardingStep.FINANCAS -> FinancasStep(answers, viewModel::updateAnswers)
                OnboardingStep.METAS -> MetasStep(answers, viewModel::updateAnswers)
                OnboardingStep.CONFIRMACAO -> ConfirmacaoStep(answers)
            }
        }
    }
}
