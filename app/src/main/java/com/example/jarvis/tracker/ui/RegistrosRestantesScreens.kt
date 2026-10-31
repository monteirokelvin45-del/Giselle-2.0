// ============================================================
// tracker/ui/RegistrosRestantesScreens.kt
// ------------------------------------------------------------
// Mental/Disciplina, Ayanokoji, Estudos/Carreira e Leitura —
// mesmo padrão de RegistroFisicoScreen/RegistroFinanceiroScreen:
// estado local simples, grava direto via DAO.insert() no Salvar.
// ============================================================

package com.example.jarvis.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jarvis.tracker.data.*
import kotlinx.coroutines.launch

@Composable
fun RegistroMentalScreen(dao: RegistroMentalDao, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var horaDormir by remember { mutableStateOf("") }
    var horaAcordar by remember { mutableStateOf("") }
    var qualidadeSono by remember { mutableIntStateOf(3) }
    var notaDisciplina by remember { mutableIntStateOf(3) }
    var interacoes by remember { mutableStateOf("") }
    var notaConforto by remember { mutableIntStateOf(3) }
    var saving by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Registrar hoje — Mental/Disciplina", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(horaDormir, { horaDormir = it }, label = { Text("Dormiu às") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(horaAcordar, { horaAcordar = it }, label = { Text("Acordou às") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        RatingRow("Qualidade do sono", qualidadeSono) { qualidadeSono = it }
        RatingRow("Nota de disciplina", notaDisciplina) { notaDisciplina = it }
        OutlinedTextField(interacoes, { interacoes = it }, label = { Text("Interações sociais hoje (qtd)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        RatingRow("Conforto social", notaConforto) { notaConforto = it }

        Button(
            onClick = {
                saving = true
                scope.launch {
                    val now = System.currentTimeMillis()
                    dao.insert(
                        RegistroMental(
                            data = now,
                            horaDormir = horaDormir.ifBlank { null },
                            horaAcordar = horaAcordar.ifBlank { null },
                            qualidadeSono = qualidadeSono,
                            notaDisciplina = notaDisciplina,
                            interacoesSociaisQtd = interacoes.toIntOrNull(),
                            notaConfortoSocial = notaConforto,
                            createdAt = now
                        )
                    )
                    saving = false; onSaved()
                }
            },
            enabled = !saving, modifier = Modifier.fillMaxWidth()
        ) { Text(if (saving) "Salvando..." else "Salvar registro") }
    }
}

@Composable
fun RegistroAyanokojiScreen(dao: AyanokojiDao, semanaAtual: Int, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var respiracao by remember { mutableStateOf(false) }
    var treinoTomNeutro by remember { mutableStateOf(false) }
    var diarioInteracoes by remember { mutableStateOf(false) }
    var notaControle by remember { mutableIntStateOf(3) }
    var observacoes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Registrar hoje — Protocolo Ayanokoji", style = MaterialTheme.typography.titleLarge)
        CheckRow("Respiração box feita", respiracao) { respiracao = it }
        CheckRow("Treino de tom neutro feito", treinoTomNeutro) { treinoTomNeutro = it }
        CheckRow("Diário de interações feito", diarioInteracoes) { diarioInteracoes = it }
        RatingRow("Controle emocional", notaControle) { notaControle = it }
        OutlinedTextField(observacoes, { observacoes = it }, label = { Text("Observações") }, modifier = Modifier.fillMaxWidth())

        Button(
            onClick = {
                saving = true
                scope.launch {
                    dao.insertRegistroDiario(
                        AyanokojiRegistroDiario(
                            data = System.currentTimeMillis(),
                            semana = semanaAtual,
                            respiracaoBoxFeita = respiracao,
                            treinoTomNeutroFeito = treinoTomNeutro,
                            diarioInteracoesFeito = diarioInteracoes,
                            notaControleEmocional = notaControle,
                            observacoes = observacoes.ifBlank { null }
                        )
                    )
                    saving = false; onSaved()
                }
            },
            enabled = !saving, modifier = Modifier.fillMaxWidth()
        ) { Text(if (saving) "Salvando..." else "Salvar registro") }
    }
}

@Composable
fun RegistroEstudoScreen(dao: RegistroEstudoDao, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var area by remember { mutableStateOf("") }
    var topico by remember { mutableStateOf("") }
    var duracao by remember { mutableStateOf("") }
    var praticaExecutada by remember { mutableStateOf(false) }
    var resumo by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Registrar hoje — Estudos/Carreira", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(area, { area = it }, label = { Text("Área") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(topico, { topico = it }, label = { Text("Tópico (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(duracao, { duracao = it }, label = { Text("Duração (minutos)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        CheckRow("Prática executada", praticaExecutada) { praticaExecutada = it }
        OutlinedTextField(resumo, { resumo = it }, label = { Text("Resumo (opcional)") }, modifier = Modifier.fillMaxWidth())

        Button(
            onClick = {
                saving = true
                scope.launch {
                    val now = System.currentTimeMillis()
                    dao.insert(
                        RegistroEstudo(
                            data = now, area = area.ifBlank { "geral" }, topico = topico.ifBlank { null },
                            duracaoMinutos = duracao.toIntOrNull() ?: 0,
                            praticaExecutada = praticaExecutada, resumo = resumo.ifBlank { null },
                            createdAt = now
                        )
                    )
                    saving = false; onSaved()
                }
            },
            enabled = !saving && area.isNotBlank() && duracao.toIntOrNull() != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (saving) "Salvando..." else "Salvar registro") }
    }
}

@Composable
fun RegistroLeituraScreen(dao: RegistroLeituraDao, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var livro by remember { mutableStateOf("") }
    var capitulo by remember { mutableStateOf("") }
    var minutos by remember { mutableStateOf("") }
    var aplicacaoPratica by remember { mutableStateOf(false) }
    var leituraBiblica by remember { mutableStateOf(false) }
    var anotacoes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Registrar hoje — Leitura", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(livro, { livro = it }, label = { Text("Livro") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(capitulo, { capitulo = it }, label = { Text("Capítulo lido (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(minutos, { minutos = it }, label = { Text("Minutos de leitura") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        CheckRow("Aplicação prática feita", aplicacaoPratica) { aplicacaoPratica = it }
        CheckRow("Leitura bíblica feita", leituraBiblica) { leituraBiblica = it }
        OutlinedTextField(anotacoes, { anotacoes = it }, label = { Text("Anotações (opcional)") }, modifier = Modifier.fillMaxWidth())

        Button(
            onClick = {
                saving = true
                scope.launch {
                    val now = System.currentTimeMillis()
                    dao.insert(
                        RegistroLeitura(
                            data = now, livro = livro.ifBlank { "sem título" },
                            capituloLido = capitulo.ifBlank { null }, minutosLeitura = minutos.toIntOrNull(),
                            aplicacaoPraticaFeita = aplicacaoPratica, anotacoesResumo = anotacoes.ifBlank { null },
                            leituraBiblicaFeita = leituraBiblica, createdAt = now
                        )
                    )
                    saving = false; onSaved()
                }
            },
            enabled = !saving && livro.isNotBlank(), modifier = Modifier.fillMaxWidth()
        ) { Text(if (saving) "Salvando..." else "Salvar registro") }
    }
}

// -----------------------------------------------------
// Componentes compartilhados pelos 4 formulários acima
// -----------------------------------------------------
@Composable
private fun RatingRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Text("$label: $value/5", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { n ->
                FilterChip(selected = value == n, onClick = { onChange(n) }, label = { Text("$n") })
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
