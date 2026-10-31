// ============================================================
// tracker/ui/DashboardScreen.kt
// ------------------------------------------------------------
// KPIs por período (item 16) + gráfico de barras real via Vico
// (item 17, Compose nativo, sem AndroidView/XML) comparando
// receitas x despesas do período selecionado.
// ============================================================

package com.example.jarvis.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jarvis.tracker.data.TrackerDatabase
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import java.util.concurrent.TimeUnit

private enum class Periodo(val dias: Long, val label: String) {
    SEMANA(7, "7 dias"), MES(30, "30 dias")
}

@Composable
fun DashboardScreen(db: TrackerDatabase) {
    var periodo by remember { mutableStateOf(Periodo.SEMANA) }
    var treinos by remember { mutableIntStateOf(0) }
    var minutosEstudo by remember { mutableIntStateOf(0) }
    var receitas by remember { mutableLongStateOf(0L) }
    var despesas by remember { mutableLongStateOf(0L) }
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(periodo) {
        val now = System.currentTimeMillis()
        val inicio = now - TimeUnit.DAYS.toMillis(periodo.dias)
        treinos = db.registroFisicoDao().countTreinosConfirmados(inicio, now)
        minutosEstudo = db.registroEstudoDao().somaMinutosNaJanela(inicio, now)
        receitas = db.registroFinanceiroDao().somaReceitas(inicio, now)
        despesas = db.registroFinanceiroDao().somaDespesas(inicio, now)
        modelProducer.runTransaction {
            columnSeries { series(receitas / 100.0, despesas / 100.0) }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Dashboard", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Periodo.entries.forEach { p ->
                FilterChip(selected = periodo == p, onClick = { periodo = p }, label = { Text(p.label) })
            }
        }

        KpiCard("Treinos confirmados", "$treinos")
        KpiCard("Minutos de estudo", "$minutosEstudo min")

        Text("Receitas x Despesas", style = MaterialTheme.typography.titleMedium)
        CartesianChartHost(
            chart = rememberCartesianChart(rememberColumnCartesianLayer()),
            modelProducer = modelProducer,
            modifier = Modifier.fillMaxWidth().height(180.dp)
        )

        KpiCard("Receitas", formatMoeda(receitas))
        KpiCard("Despesas", formatMoeda(despesas))
        KpiCard("Saldo", formatMoeda(receitas - despesas))
    }
}

@Composable
private fun KpiCard(label: String, value: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

private fun formatMoeda(centavos: Long): String = "%.2f".format(centavos / 100.0)
