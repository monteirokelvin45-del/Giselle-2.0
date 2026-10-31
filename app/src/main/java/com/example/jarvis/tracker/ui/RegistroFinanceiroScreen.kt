// ============================================================
// tracker/ui/RegistroFinanceiroScreen.kt
// ------------------------------------------------------------
// Segundo formulário de registro real — grava em
// RegistroFinanceiroDao.insert(). O tipo é um seletor explícito
// (nunca inferido de texto livre) porque TRANSFERENCIA precisa
// ficar fora dos KPIs de receita/despesa com certeza absoluta.
// ============================================================

package com.example.jarvis.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jarvis.tracker.data.RegistroFinanceiro
import com.example.jarvis.tracker.data.RegistroFinanceiroDao
import com.example.jarvis.tracker.data.TipoTransacao
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistroFinanceiroScreen(
    dao: RegistroFinanceiroDao,
    moedaPadrao: String,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var tipo by remember { mutableStateOf(TipoTransacao.DESPESA) }
    var categoria by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var contaOrigem by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var tipoMenuExpanded by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Registrar transação — Finanças", style = MaterialTheme.typography.titleLarge)

        ExposedDropdownMenuBox(
            expanded = tipoMenuExpanded,
            onExpandedChange = { tipoMenuExpanded = it }
        ) {
            OutlinedTextField(
                value = tipo.name, onValueChange = {}, readOnly = true,
                label = { Text("Tipo") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tipoMenuExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = tipoMenuExpanded, onDismissRequest = { tipoMenuExpanded = false }) {
                TipoTransacao.entries.forEach { opcao ->
                    DropdownMenuItem(
                        text = { Text(opcao.name) },
                        onClick = { tipo = opcao; tipoMenuExpanded = false }
                    )
                }
            }
        }

        if (tipo == TipoTransacao.TRANSFERENCIA) {
            Text(
                "Transferências não entram nos totais de receita/despesa — servem só para mover dinheiro entre contas.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedTextField(
            value = valor, onValueChange = { valor = it },
            label = { Text("Valor ($moedaPadrao)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = categoria, onValueChange = { categoria = it },
            label = { Text("Categoria (ex: alimentação, salário)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = descricao, onValueChange = { descricao = it },
            label = { Text("Descrição (opcional)") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = contaOrigem, onValueChange = { contaOrigem = it },
            label = { Text("Conta de origem (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                saving = true
                scope.launch {
                    val now = System.currentTimeMillis()
                    dao.insert(
                        RegistroFinanceiro(
                            data = now,
                            tipo = tipo.name,
                            categoria = categoria.ifBlank { "sem categoria" },
                            valorCentavos = (valor.toDoubleOrNull() ?: 0.0).times(100).toLong(),
                            descricao = descricao.ifBlank { null },
                            contaOrigem = contaOrigem.ifBlank { null },
                            createdAt = now
                        )
                    )
                    saving = false
                    onSaved()
                }
            },
            enabled = !saving && valor.toDoubleOrNull() != null && categoria.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (saving) "Salvando..." else "Salvar transação")
        }
    }
}
