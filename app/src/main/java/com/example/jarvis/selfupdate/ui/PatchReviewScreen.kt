// ============================================================
// selfupdate/ui/PatchReviewScreen.kt
// ------------------------------------------------------------
// Único lugar onde um PatchSuggestion muda de status. Aprovar
// aqui NÃO aplica o patch ao código-fonte rodando — só marca
// status = APROVADO e exporta o diff pra um arquivo em Downloads,
// pronto pra você aplicar manualmente (git apply) na próxima
// sessão de desenvolvimento. Ver o comentário de topo em
// SelfDiagnosisAgent.kt para o porquê disso ser um limite técnico
// do Android, não uma escolha arbitrária.
// ============================================================

package com.example.jarvis.selfupdate.ui

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jarvis.selfupdate.PatchSafetyGate
import com.example.jarvis.selfupdate.PatchStatus
import com.example.jarvis.selfupdate.PatchSuggestionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** Contrato mínimo que o ViewModel real precisa satisfazer — mantém a tela testável sem Room de verdade. */
interface PatchReviewActions {
    val pendentes: Flow<List<PatchSuggestionEntity>>
    suspend fun aprovar(patch: PatchSuggestionEntity)
    suspend fun rejeitar(patch: PatchSuggestionEntity)
}

@Composable
fun PatchReviewScreen(actions: PatchReviewActions, context: Context) {
    val patches by actions.pendentes.collectAsStateWithLifecycle(initialValue = emptyList())
    var patchSelecionado by remember { mutableStateOf<PatchSuggestionEntity?>(null) }
    val scope = rememberCoroutineScope()

    if (patches.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhum patch pendente de revisão.", color = Color.Gray)
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(patches, key = { it.id }) { patch ->
            PatchCard(patch, onAbrir = { patchSelecionado = patch })
        }
    }

    patchSelecionado?.let { patch ->
        PatchDetalheDialog(
            patch = patch,
            onFechar = { patchSelecionado = null },
            onAprovar = {
                scope.launch {
                    exportarDiffParaDownloads(context, patch)
                    actions.aprovar(patch)
                    patchSelecionado = null
                }
            },
            onRejeitar = {
                scope.launch {
                    actions.rejeitar(patch)
                    patchSelecionado = null
                }
            }
        )
    }
}

@Composable
private fun PatchCard(patch: PatchSuggestionEntity, onAbrir: () -> Unit) {
    val confianca = PatchSafetyGate.confiancaEfetiva(patch)
    val corConfianca = when (confianca) {
        "alta" -> Color(0xFF2E7D32)
        "media" -> Color(0xFFF9A825)
        else -> Color(0xFFC62828)
    }

    Card(
        onClick = onAbrir,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(patch.arquivoAlvo, fontWeight = FontWeight.Bold)
                AssistChip(
                    onClick = {},
                    label = { Text("confiança: $confianca") },
                    colors = AssistChipDefaults.assistChipColors(labelColor = corConfianca)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                patch.diagnostico,
                maxLines = 2,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun PatchDetalheDialog(
    patch: PatchSuggestionEntity,
    onFechar: () -> Unit,
    onAprovar: () -> Unit,
    onRejeitar: () -> Unit
) {
    val confianca = PatchSafetyGate.confiancaEfetiva(patch)

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(patch.arquivoAlvo) },
        text = {
            Column {
                if (confianca == "baixa" && patch.confiancaDiagnostico != "baixa") {
                    Text(
                        "⚠ Rebaixado para baixa confiança: este arquivo é de segurança " +
                        "(SafetyGate/AuditLog). Revise com atenção extra.",
                        color = Color(0xFFC62828),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text("Diagnóstico:", fontWeight = FontWeight.Bold)
                Text(patch.diagnostico)
                Spacer(Modifier.height(8.dp))
                Text("Patch sugerido:", fontWeight = FontWeight.Bold)
                Surface(color = Color(0xFF1C1C1C), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        patch.patchDiff,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF80CBC4)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Aprovar aqui não altera o app rodando — exporta o diff pra " +
                    "Downloads/ pra você aplicar manualmente na próxima build.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAprovar) { Text("Aprovar e exportar diff") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRejeitar) { Text("Rejeitar") }
                TextButton(onClick = onFechar) { Text("Fechar") }
            }
        }
    )
}

/** Salva o diff aprovado como arquivo de texto, pronto pra `git apply`. */
private fun exportarDiffParaDownloads(context: Context, patch: PatchSuggestionEntity) {
    val nomeArquivo = "jarvis-patch-${patch.id}-${
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
    }.diff"
    val pasta = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: context.filesDir
    File(pasta, nomeArquivo).writeText(patch.patchDiff)
}
