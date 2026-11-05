// ui/ArtefatosScreen.kt — propostas, planos e posts do módulo de negócio à espera da tua decisão.
// Aprovar só marca como aprovado: quem envia ou publica és tu.
package com.example.jarvis.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jarvis.business.ApprovalItem
import com.example.jarvis.business.GiselleBusiness
import com.example.jarvis.business.Kind
import kotlinx.coroutines.launch

@Composable
fun ArtefatosScreen(context: Context) {
    val scope = rememberCoroutineScope()
    var itens by remember { mutableStateOf<List<ApprovalItem>>(emptyList()) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var versao by remember { mutableIntStateOf(0) }

    LaunchedEffect(versao) { itens = GiselleBusiness.pendentes(context) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Artefatos do negócio", style = MaterialTheme.typography.titleMedium)
        Text(
            "Propostas, planos e posts esperam a tua decisão. Aprovar não envia nada: quem envia és tu.",
            style = MaterialTheme.typography.bodySmall,
        )
        aviso?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))

        if (itens.isEmpty()) {
            Text("Nenhum artefato pendente.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(itens, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("#${item.id} · ${rotulo(item.kind)}", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(item.text, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    scope.launch {
                                        aviso = GiselleBusiness.command(context, "aprovar ${item.id}")
                                        versao++
                                    }
                                }) { Text("Aprovar") }
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        aviso = GiselleBusiness.command(context, "negar ${item.id}")
                                        versao++
                                    }
                                }) { Text("Negar") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun rotulo(kind: String): String = when (kind) {
    Kind.PROPOSAL -> "proposta"
    Kind.PLAN -> "plano"
    Kind.POST -> "posts"
    Kind.SPEND -> "gasto"
    Kind.PAYOUT -> "conciliação"
    else -> kind.lowercase()
}
