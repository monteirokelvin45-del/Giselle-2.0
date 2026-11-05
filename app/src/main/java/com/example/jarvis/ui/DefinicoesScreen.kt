// ui/DefinicoesScreen.kt — definições: atualizações, dados, acessibilidade e o motor de negócio.
package com.example.jarvis.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.jarvis.business.GiselleBusiness

@Composable
fun DefinicoesScreen(
    onAtualizacoes: () -> Unit,
    onExportarBackup: () -> Unit,
    onRestaurarBackup: () -> Unit,
    onAcessibilidade: () -> Unit,
) {
    val context = LocalContext.current
    var estado by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Secao("Atualizações e dados")
        Linha("Atualizações", onAtualizacoes)
        Linha("Exportar backup", onExportarBackup)
        Linha("Restaurar backup", onRestaurarBackup)

        Secao("Sistema")
        Linha("Permissão de acessibilidade", onAcessibilidade)

        Secao("Segundo plano (Huawei/EMUI)")
        Linha("Permitir execução em segundo plano") { abrirAjustesDeSegundoPlano(context) }

        Secao("Negócio (supervisionado)")
        Linha("Ligar motor de negócio (de hora a hora, com rede)") {
            GiselleBusiness.start(context)
            estado = "Motor de negócio ligado."
        }
        Linha("Desligar motor de negócio") {
            GiselleBusiness.stop(context)
            estado = "Motor de negócio desligado."
        }
        estado?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Secao(titulo: String) {
    Text(
        titulo,
        Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Linha(titulo: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(titulo) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Abre as telas de inicialização/apps protegidos do EMUI, quando existirem.
 * Os nomes de componente são os da Huawei e não foram verificados em todos os modelos:
 * se nenhum abrir, cai nos detalhes do app, onde a bateria também pode ser ajustada.
 */
private fun abrirAjustesDeSegundoPlano(context: Context) {
    val candidatos = listOf(
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
    )
    for (componente in candidatos) {
        try {
            context.startActivity(Intent().setComponent(componente).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // tenta o próximo
        }
    }
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
