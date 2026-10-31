package com.example.jarvis.updates

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

// ===================== Worker (verificação automática) =====================

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val updater = GiselleUpdater(applicationContext)
        val state = runCatching { updater.checkForUpdates() }.getOrElse { return Result.retry() }

        // Configuração: aplica sozinha (se ativado). Entra em vigor no próximo ciclo do agente.
        state.configUpdate?.let { if (updater.autoApplyConfig) updater.applyConfig(it) }

        // APK: nunca instala sozinho — só avisa.
        state.appUpdate?.let { notifyAppUpdate(it) }
        return Result.success()
    }

    private fun notifyAppUpdate(r: AppRelease) {
        val c = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("giselle_updates", "Atualizações", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = c.packageManager.getLaunchIntentForPackage(c.packageName)
        val pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, "giselle_updates")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Giselle ${r.versionName} disponível")
            .setContentText(r.notes.ifBlank { "Toca para atualizar" })
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(1001, n) }
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("giselle_updates", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

// ===================== ViewModel =====================

data class UiState(
    val busy: Boolean = false,
    val progress: Int = -1,
    val state: UpdateState = UpdateState(),
    val message: String = "",
    val autoConfig: Boolean = true,
)

class UpdatesViewModel(app: Application) : AndroidViewModel(app) {
    private val updater = GiselleUpdater(app)
    var ui by mutableStateOf(UiState(autoConfig = updater.autoApplyConfig))
        private set

    val appVersionName: String get() = updater.installedAppName()

    fun check() = viewModelScope.launch {
        ui = ui.copy(busy = true, message = "")
        ui = runCatching { updater.checkForUpdates() }.fold(
            onSuccess = { ui.copy(busy = false, state = it, message = if (it.appUpdate == null && it.configUpdate == null) "Tudo em dia." else "") },
            onFailure = { ui.copy(busy = false, message = "Falha ao verificar: ${it.message}") },
        )
    }

    fun applyConfig() = viewModelScope.launch {
        val r = ui.state.configUpdate ?: return@launch
        ui = ui.copy(busy = true)
        updater.applyConfig(r)
            .onSuccess { ui = ui.copy(busy = false, message = "Configuração v${r.version} aplicada."); check() }
            .onFailure { ui = ui.copy(busy = false, message = "Rejeitada: ${it.message}") }
    }

    fun updateApp() = viewModelScope.launch {
        val r = ui.state.appUpdate ?: return@launch
        ui = ui.copy(busy = true, progress = 0)
        updater.downloadApk(r) { p -> ui = ui.copy(progress = p) }
            .onSuccess {
                val ok = updater.install(it)
                ui = ui.copy(busy = false, progress = -1,
                    message = if (ok) "Confirma a instalação no ecrã do Android." else "Ativa 'instalar apps desconhecidos' para a Giselle e toca de novo.")
            }
            .onFailure { ui = ui.copy(busy = false, progress = -1, message = "APK rejeitado: ${it.message}") }
    }

    fun rollback() {
        updater.rollback()
        ui = ui.copy(message = "Configuração revertida.", state = ui.state.copy(installedConfigVersion = updater.installedConfigVersion()))
    }

    fun setAuto(v: Boolean) {
        updater.autoApplyConfig = v
        ui = ui.copy(autoConfig = v)
    }
}

// ===================== Tela =====================

@Composable
fun UpdatesScreen(vm: UpdatesViewModel = viewModel()) {
    val ui = vm.ui
    LaunchedEffect(Unit) { vm.check() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Giselle atualizações", style = MaterialTheme.typography.headlineSmall)
        Text("App v${vm.appVersionName} · Config v${ui.state.installedConfigVersion}")

        ui.state.appUpdate?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nova versão ${r.versionName}", style = MaterialTheme.typography.titleMedium)
                    if (r.notes.isNotBlank()) Text(r.notes)
                    if (ui.progress in 0..100) LinearProgressIndicator(progress = { ui.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = vm::updateApp, enabled = !ui.busy) { Text("Baixar e instalar") }
                }
            }
        }

        ui.state.configUpdate?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nova configuração v${r.version}", style = MaterialTheme.typography.titleMedium)
                    if (r.notes.isNotBlank()) Text(r.notes)
                    Button(onClick = vm::applyConfig, enabled = !ui.busy) { Text("Aplicar agora") }
                }
            }
        }

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Aplicar configurações automaticamente", Modifier.weight(1f))
            Switch(checked = ui.autoConfig, onCheckedChange = vm::setAuto)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.check() }, enabled = !ui.busy) { Text("Verificar agora") }
            OutlinedButton(onClick = vm::rollback, enabled = !ui.busy) { Text("Reverter config") }
        }

        if (ui.busy && ui.progress < 0) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (ui.message.isNotBlank()) Text(ui.message)
    }
}
