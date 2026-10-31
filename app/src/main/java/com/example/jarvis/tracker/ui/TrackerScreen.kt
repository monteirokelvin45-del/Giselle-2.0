// ============================================================
// tracker/ui/TrackerScreen.kt
// ------------------------------------------------------------
// Tela do modo Tracker: lista de insights gerados pela IA,
// cada um com Aceitar/Recusar/Ignorar — o mesmo espírito da
// tela de Confirmações do núcleo, mas para sugestões em vez
// de ações executáveis.
// ============================================================

package com.example.jarvis.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jarvis.tracker.agent.TrackerFeedback
import com.example.jarvis.tracker.agent.TrackerRepository
import com.example.jarvis.tracker.data.AiEvent

@Composable
fun TrackerScreen(
    onGenerateInsight: () -> Unit,
    onResolveFeedback: (Long, TrackerFeedback) -> Unit
) {
    val insights by TrackerRepository.insights.collectAsStateWithLifecycle()
    val statusMessage by TrackerRepository.statusMessage.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Diagnóstico", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onGenerateInsight) { Text("Gerar agora") }
        }

        statusMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        Spacer(Modifier.height(12.dp))

        if (insights.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nenhum diagnóstico ainda. Toque em \"Gerar agora\" quando tiver\nalguns dias de registros confirmados.",
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(insights) { event -> InsightCard(event, onResolveFeedback) }
        }
    }
}

@Composable
fun InsightCard(event: AiEvent, onResolveFeedback: (Long, TrackerFeedback) -> Unit) {
    val alreadyResolved = event.feedback != null

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            AssistChip(onClick = {}, label = { Text(event.tipo) })
            Spacer(Modifier.height(8.dp))
            Text(event.sugestaoTexto, style = MaterialTheme.typography.bodyLarge)

            if (alreadyResolved) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Você marcou como: ${event.feedback}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onResolveFeedback(event.id, TrackerFeedback.ACEITA) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) { Text("Aceitar") }
                    OutlinedButton(
                        onClick = { onResolveFeedback(event.id, TrackerFeedback.RECUSADA) }
                    ) { Text("Recusar") }
                    TextButton(
                        onClick = { onResolveFeedback(event.id, TrackerFeedback.IGNORADA) }
                    ) { Text("Ignorar") }
                }
            }
        }
    }
}
