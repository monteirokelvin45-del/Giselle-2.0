package com.example.jarvis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.jarvis.BuildConfig
import com.example.jarvis.core.BackgroundSurvival

/** Avisos discretos de configuração: proxy de IA, bateria e arranque automático. Some quando tudo está OK. */
@Composable
fun SurvivalBanner() {
    val ctx = LocalContext.current
    var whitelisted by remember { mutableStateOf(BackgroundSurvival.isWhitelisted(ctx)) }
    var autostartDone by remember { mutableStateOf(BackgroundSurvival.autostartDone(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { whitelisted = BackgroundSurvival.isWhitelisted(ctx) }

    val noProxy = BuildConfig.PROXY_BASE_URL.isBlank()
    val needAutostart = BackgroundSurvival.needsAutostartStep() && !autostartDone
    if (!noProxy && whitelisted && !needAutostart) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (noProxy) {
                Text("Proxy de IA em falta", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Esta versão foi compilada sem PROXY_BASE_URL. Segue backend-proxy/SETUP_PROXY.md e compila de novo.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!whitelisted) {
                Text("Mantém a Giselle sempre ativa", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Sem esta permissão o Android pode fechá-la para poupar bateria.",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { BackgroundSurvival.requestWhitelist(ctx) }) { Text("Permitir") }
            }
            if (needAutostart) {
                Text("Arranque automático", style = MaterialTheme.typography.titleSmall)
                Text(
                    "No teu telemóvel, ativa também o arranque automático e a execução em segundo plano.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { BackgroundSurvival.openAutostart(ctx) }) { Text("Abrir definições") }
                    TextButton(onClick = {
                        BackgroundSurvival.markAutostartDone(ctx); autostartDone = true
                    }) { Text("Já fiz") }
                }
            }
        }
    }
}
