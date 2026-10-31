// ============================================================
// tracker/ui/RegistroFisicoScreen.kt
// ------------------------------------------------------------
// Primeiro formulário de registro real do módulo — grava direto
// em RegistroFisicoDao.insert(). treinoConfirmado é um toggle
// explícito do usuário, nunca inferido — mesma regra do schema
// original (RegistroFisico.treinoConfirmado: "false = parcialmente
// concluído", nunca um default silencioso para true).
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
import com.example.jarvis.tracker.data.RegistroFisico
import com.example.jarvis.tracker.data.RegistroFisicoDao
import kotlinx.coroutines.launch

@Composable
fun RegistroFisicoScreen(
    dao: RegistroFisicoDao,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pesoKg by remember { mutableStateOf("") }
    var gorduraPercentual by remember { mutableStateOf("") }
    var cinturaCm by remember { mutableStateOf("") }
    var bracoCm by remember { mutableStateOf("") }
    var treinoResumo by remember { mutableStateOf("") }
    var treinoConfirmado by remember { mutableStateOf(false) }
    var observacoes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Registrar hoje — Físico", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = pesoKg, onValueChange = { pesoKg = it },
            label = { Text("Peso (kg)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = gorduraPercentual, onValueChange = { gorduraPercentual = it },
            label = { Text("% de gordura (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = cinturaCm, onValueChange = { cinturaCm = it },
                label = { Text("Cintura (cm)") }, modifier = Modifier.weight(1f), singleLine = true
            )
            OutlinedTextField(
                value = bracoCm, onValueChange = { bracoCm = it },
                label = { Text("Braço (cm)") }, modifier = Modifier.weight(1f), singleLine = true
            )
        }

        HorizontalDivider()

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Treinou hoje?", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = treinoConfirmado, onCheckedChange = { treinoConfirmado = it })
        }

        if (treinoConfirmado) {
            OutlinedTextField(
                value = treinoResumo, onValueChange = { treinoResumo = it },
                label = { Text("Resumo do treino (ex: peito e tríceps, 5 exercícios)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = observacoes, onValueChange = { observacoes = it },
            label = { Text("Observações (opcional)") }, modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                saving = true
                scope.launch {
                    val now = System.currentTimeMillis()
                    dao.insert(
                        RegistroFisico(
                            data = now,
                            pesoKg = pesoKg.toDoubleOrNull(),
                            gorduraPercentual = gorduraPercentual.toDoubleOrNull(),
                            medidasCinturaCm = cinturaCm.toDoubleOrNull(),
                            medidasBracoCm = bracoCm.toDoubleOrNull(),
                            treinoDiaResumo = treinoResumo.ifBlank { null },
                            treinoConfirmado = treinoConfirmado,
                            observacoes = observacoes.ifBlank { null },
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    saving = false
                    onSaved()
                }
            },
            enabled = !saving && pesoKg.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (saving) "Salvando..." else "Salvar registro")
        }
    }
}
