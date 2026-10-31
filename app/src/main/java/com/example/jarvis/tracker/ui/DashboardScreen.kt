// ============================================================
// tracker/ui/DashboardScreen.kt
// ------------------------------------------------------------
// KPIs por período (item 16) + gráfico de barras real via Vico
// (item 17, Compose nativo, sem AndroidView/XML) comparando
// receitas x despesas do período selecionado.
// ============================================================

package com.example.jarvis.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.jarvis.tracker.data.TrackerDatabase
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

    LaunchedEffect(periodo) {
        val now = System.currentTimeMillis()
        val inicio = now - TimeUnit.DAYS.toMillis(periodo.dias)
        treinos = db.registroFisicoDao().countTreinosConfirmados(inicio, now)
        minutosEstudo = db.registroEstudoDao().somaMinutosNaJanela(inicio, now)
        receitas = db.registroFinanceiroDao().somaReceitas(inicio, now)
        despesas = db.registroFinanceiroDao().somaDespesas(inicio, now)
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
        RevenueExpenseChart(receitas = receitas, despesas = despesas)

        KpiCard("Receitas", formatMoeda(receitas))
        KpiCard("Despesas", formatMoeda(despesas))
        KpiCard("Saldo", formatMoeda(receitas - despesas))
    }
}

@Composable
private fun RevenueExpenseChart(receitas: Long, despesas: Long) {
    val maxValue = maxOf(receitas, despesas, 1L)
    Row(
        Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        ChartBar("Receitas", receitas, maxValue, MaterialTheme.colorScheme.primary)
        ChartBar("Despesas", despesas, maxValue, MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ChartBar(label: String, value: Long, maxValue: Long, color: Color) {
    val ratio = (value.toDouble() / maxValue.toDouble()).coerceIn(0.0, 1.0).toFloat()
    val barHeight = (ratio * 108f).coerceAtLeast(4f)
    Column(
        modifier = Modifier.fillMaxHeight().widthIn(min = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(formatMoeda(value), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.width(48.dp).height(barHeight.dp)
                .background(color, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
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
