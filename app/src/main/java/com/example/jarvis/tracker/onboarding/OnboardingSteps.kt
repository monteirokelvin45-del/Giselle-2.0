// ============================================================
// tracker/onboarding/OnboardingSteps.kt
// ------------------------------------------------------------
// Um composable por etapa. Cada um só lê/escreve o pedaço de
// OnboardingAnswers que lhe cabe, via updateAnswers(transform) —
// nunca conhece a etapa anterior/seguinte.
// ============================================================

package com.example.jarvis.tracker.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private typealias Update = ((OnboardingAnswers) -> OnboardingAnswers) -> Unit

@Composable
fun PerfilStep(answers: OnboardingAnswers, update: Update) {
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Vamos começar com o básico.", style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = answers.nome ?: "", onValueChange = { v -> update { it.copy(nome = v) } },
            label = { Text("Seu nome") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = answers.idade?.toString() ?: "",
            onValueChange = { v -> update { it.copy(idade = v.toIntOrNull()) } },
            label = { Text("Idade") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = answers.alturaCm?.toString() ?: "",
            onValueChange = { v -> update { it.copy(alturaCm = v.toIntOrNull()) } },
            label = { Text("Altura (cm)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = answers.pesoInicialKg?.toString() ?: "",
            onValueChange = { v -> update { it.copy(pesoInicialKg = v.toDoubleOrNull()) } },
            label = { Text("Peso atual (kg)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = answers.objetivoPrincipal ?: "",
            onValueChange = { v -> update { it.copy(objetivoPrincipal = v) } },
            label = { Text("Objetivo principal (ex: hipertrofia, emagrecimento)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = answers.nivelExperienciaTreino ?: "",
            onValueChange = { v -> update { it.copy(nivelExperienciaTreino = v) } },
            label = { Text("Nível de experiência com treino") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TreinoStep(answers: OnboardingAnswers, update: Update) {
    val diasLabel = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Em quais dias você treina? (opcional — pode pular)", style = MaterialTheme.typography.bodyMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            diasLabel.forEachIndexed { index, label ->
                val dia = index + 1
                FilterChip(
                    selected = dia in answers.diasTreinoSemana,
                    onClick = {
                        update {
                            val novos = if (dia in it.diasTreinoSemana) it.diasTreinoSemana - dia else it.diasTreinoSemana + dia
                            it.copy(diasTreinoSemana = novos.sorted())
                        }
                    },
                    label = { Text(label) }
                )
            }
        }

        answers.diasTreinoSemana.sorted().forEach { dia ->
            OutlinedTextField(
                value = answers.grupoMuscularPorDia[dia] ?: "",
                onValueChange = { v ->
                    update { it.copy(grupoMuscularPorDia = it.grupoMuscularPorDia + (dia to v)) }
                },
                label = { Text("Grupo muscular — ${diasLabel[dia - 1]}") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SonoHabitosStep(answers: OnboardingAnswers, update: Update) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Seus horários habituais de sono.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = answers.horaDormirHabitual ?: "",
            onValueChange = { v -> update { it.copy(horaDormirHabitual = v) } },
            label = { Text("Hora que costuma dormir (ex: 23:00)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = answers.horaAcordarHabitual ?: "",
            onValueChange = { v -> update { it.copy(horaAcordarHabitual = v) } },
            label = { Text("Hora que costuma acordar (ex: 06:30)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
    }
}

@Composable
fun EstudosCarreiraStep(answers: OnboardingAnswers, update: Update) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Seus estudos hoje.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = answers.areaEstudoPrincipal ?: "",
            onValueChange = { v -> update { it.copy(areaEstudoPrincipal = v) } },
            label = { Text("Área principal de estudo") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = answers.horasEstudoSemanaAtual?.toString() ?: "",
            onValueChange = { v -> update { it.copy(horasEstudoSemanaAtual = v.toIntOrNull()) } },
            label = { Text("Horas de estudo por semana hoje") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
    }
}

@Composable
fun FinancasStep(answers: OnboardingAnswers, update: Update) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sua base financeira.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = answers.moeda,
            onValueChange = { v -> update { it.copy(moeda = v) } },
            label = { Text("Moeda (ex: MZN, BRL, USD)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = answers.rendaMensalAtual?.toString() ?: "",
            onValueChange = { v -> update { it.copy(rendaMensalAtual = v.toDoubleOrNull()) } },
            label = { Text("Renda mensal atual") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
    }
}

@Composable
fun MetasStep(answers: OnboardingAnswers, update: Update) {
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Suas metas — onde você quer chegar.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = answers.pesoIdealKg?.toString() ?: "",
            onValueChange = { v -> update { it.copy(pesoIdealKg = v.toDoubleOrNull()) } },
            label = { Text("Peso ideal (kg)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = answers.rendaMensalAlvo?.toString() ?: "",
            onValueChange = { v -> update { it.copy(rendaMensalAlvo = v.toDoubleOrNull()) } },
            label = { Text("Renda mensal alvo") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = answers.horasEstudoSemanaAlvo?.toString() ?: "",
            onValueChange = { v -> update { it.copy(horasEstudoSemanaAlvo = v.toIntOrNull()) } },
            label = { Text("Horas de estudo/semana alvo") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = answers.reservaFinanceiraAlvo?.toString() ?: "",
            onValueChange = { v -> update { it.copy(reservaFinanceiraAlvo = v.toDoubleOrNull()) } },
            label = { Text("Reserva financeira alvo") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = answers.visao3Meses ?: "",
            onValueChange = { v -> update { it.copy(visao3Meses = v) } },
            label = { Text("Visão para 3 meses") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = answers.visao12Meses ?: "",
            onValueChange = { v -> update { it.copy(visao12Meses = v) } },
            label = { Text("Visão para 12 meses") }, modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ConfirmacaoStep(answers: OnboardingAnswers) {
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Confira antes de confirmar:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        ResumoLinha("Nome", answers.nome)
        ResumoLinha("Idade", answers.idade?.toString())
        ResumoLinha("Objetivo", answers.objetivoPrincipal)
        ResumoLinha("Dias de treino/semana", answers.diasTreinoSemana.size.takeIf { it > 0 }?.toString())
        ResumoLinha("Moeda", answers.moeda)
        ResumoLinha("Peso ideal", answers.pesoIdealKg?.toString())
        ResumoLinha("Visão 12 meses", answers.visao12Meses)
        Spacer(Modifier.height(12.dp))
        Text(
            "Depois de confirmar, seu perfil passa a ser usado pela Giselle para gerar diagnósticos.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ResumoLinha(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value ?: "—", style = MaterialTheme.typography.bodyMedium)
    }
}
