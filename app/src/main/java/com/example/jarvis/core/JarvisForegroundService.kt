// ============================================================
// core/JarvisForegroundService.kt
// ------------------------------------------------------------
// Mantém o cérebro vivo mesmo com app em background.
// É aqui que instanciamos o Orchestrator e conectamos tudo:
// LLM client, memória, safety gate, voz, launcher de apps,
// log auditável e as proteções de rate limit / circuit breaker.
//
// Sem isso, o Android mata o processo e o "aprendizado em
// tempo real" para de funcionar assim que você minimiza o app.
// ============================================================

package com.example.jarvis.core

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.jarvis.BuildConfig
import com.example.jarvis.accessibility.JarvisAccessibilityServiceRef
import com.example.jarvis.agent.AccessibilityScreenActuator
import com.example.jarvis.agent.AgentOrchestrator
import com.example.jarvis.agent.ClaudeDecisionMaker
import com.example.jarvis.agent.DefaultAppLauncherPort
import com.example.jarvis.agent.IntentExecutor
import com.example.jarvis.llm.RoutedDecisionMaker
import com.example.jarvis.routine.DailyBrief
import com.example.jarvis.skills.SkillStore
import com.example.jarvis.llm.LocalLlmDecisionMaker
import com.example.jarvis.routine.RoutineScheduler
import com.example.jarvis.vision.VisionExecutor
import com.example.jarvis.agent.PendingAction
import com.example.jarvis.agent.RoomAuditPort
import com.example.jarvis.agent.RoomMemoryPort
import com.example.jarvis.llm.ClaudeClient
import com.example.jarvis.llm.ProxyAuth
import com.example.jarvis.memory.ProxyEmbedder
import com.example.jarvis.memory.JarvisDatabase
import com.example.jarvis.memory.MemoryStore
import com.example.jarvis.voice.VoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class JarvisForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "jarvis_core_channel"
        const val ALERT_CHANNEL_ID = "jarvis_alert_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_USER_COMMAND = "com.example.jarvis.ACTION_USER_COMMAND"
        const val ACTION_SPEAK = "com.example.jarvis.ACTION_SPEAK"
        const val ACTION_AUTONOMOUS_TICK = "com.example.jarvis.ACTION_AUTONOMOUS_TICK"
        const val ACTION_RESOLVE_CONFIRMATION = "com.example.jarvis.ACTION_RESOLVE_CONFIRMATION"
        const val ACTION_REFRESH_MEMORIES = "com.example.jarvis.ACTION_REFRESH_MEMORIES"
        const val EXTRA_COMMAND = "extra_command"
        const val EXTRA_APPROVED = "extra_approved"
        const val EXTRA_PENDING_CREATED_AT = "extra_pending_created_at"
    }

    private lateinit var orchestrator: AgentOrchestrator
    private lateinit var voiceEngine: VoiceEngine
    private lateinit var memoryStore: MemoryStore
    private lateinit var auditLog: AuditLog
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        val db = JarvisDatabase.getInstance(applicationContext)

        // Embeddings reais via Worker (op "embed"). Sem proxy, a busca semântica fica desligada
        // em vez de usar um hash sem significado. Nenhuma chave no APK.
        val embedder = ProxyEmbedder(
            proxyBaseUrl = BuildConfig.PROXY_BASE_URL.ifBlank { null },
            proxySharedSecret = BuildConfig.PROXY_SHARED_SECRET.ifBlank { null },
            clientId = ProxyAuth.clientId(applicationContext),
        )

        memoryStore = MemoryStore(db.memoryDao(), embedder)
        auditLog = AuditLog(db.auditLogDao())

        // Item 3: key lida via BuildConfig (local.properties), nunca hard-coded.
        val claudeClient = ClaudeClient(
            proxyBaseUrl = BuildConfig.PROXY_BASE_URL.ifBlank { null },
            proxySharedSecret = BuildConfig.PROXY_SHARED_SECRET.ifBlank { null },
            proxyClientId = ProxyAuth.clientId(applicationContext),
            extraInstructions = {
                com.example.jarvis.updates.GiselleUpdater(applicationContext)
                    .currentConfig()?.optString("systemPrompt")
            }
        )
        val appLauncher = AppLauncher(applicationContext)
        voiceEngine = VoiceEngine(applicationContext).apply { init() }
        serviceScope.launch { GiselleBus.announce.collect { voiceEngine.speak(it) } }

        orchestrator = AgentOrchestrator(
            claude = RoutedDecisionMaker(ClaudeDecisionMaker(claudeClient), LocalLlmDecisionMaker(applicationContext, claudeClient)),
            memory = RoomMemoryPort(memoryStore),
            auditLog = RoomAuditPort(auditLog),
            appLauncher = DefaultAppLauncherPort(appLauncher),
            screen = AccessibilityScreenActuator(),
            rateLimiter = RateLimiter(maxCallsPerWindow = 20, windowMs = 60_000L),
            circuitBreaker = CircuitBreaker(failureThreshold = 5, openDurationMs = 60_000L),
            onNeedsConfirmation = { pending ->
                notifyConfirmationNeeded(pending)
                JarvisRepository.addPendingAction(pending)
                JarvisRepository.pushFeedItem(
                    FeedItem(text = "Preciso confirmar: ${pending.decision.userFacingSummary}", type = FeedItemType.CONFIRMATION_ASKED)
                )
            },
            onSpeak = { text ->
                voiceEngine.speak(text)
                JarvisRepository.pushFeedItem(FeedItem(text = text, type = FeedItemType.SPOKEN))
            },
            visionPort = VisionExecutor(claudeClient, applicationContext),
            appContext = applicationContext,
            onHealthyCycle = { com.example.jarvis.updates.GiselleUpdater(applicationContext).markHealthy() },
            intents = IntentExecutor(applicationContext),
            screenReader = { (JarvisAccessibilityServiceRef.currentPackageName() ?: "desconhecido") to JarvisAccessibilityServiceRef.snapshot() },
            onCircuitOpen = { reason ->
                JarvisRepository.pushFeedItem(
                    FeedItem(text = "Pausei as ações automáticas por segurança: $reason", type = FeedItemType.ACTION_FAILED)
                )
            }
        )
        orchestrator.webSearchPort = com.example.jarvis.web.WebSearchClient(
            proxyBaseUrl = BuildConfig.PROXY_BASE_URL.ifBlank { null },
            proxySharedSecret = BuildConfig.PROXY_SHARED_SECRET.ifBlank { null },
            clientId = ProxyAuth.clientId(applicationContext),
        )

        JarvisRepository.setServiceRunning(true)
        RoutineScheduler.rescheduleAll(applicationContext)
        SkillStore.refresh(applicationContext)
        resumeLongTask()
    }

    /** Tarefa longa interrompida (processo morto)? Retoma uma vez. */
    private fun resumeLongTask() {
        val cmd = getSharedPreferences("giselle_long", MODE_PRIVATE).getString("pending", null) ?: return
        serviceScope.launch {
            kotlinx.coroutines.delay(4000)
            orchestrator.processTask(cmd, currentForegroundApp(), JarvisAccessibilityServiceRef.snapshot())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Giselle ativa — observando"))

        when (intent?.action) {
            ACTION_USER_COMMAND -> {
                val command = intent.getStringExtra(EXTRA_COMMAND)
                val app = currentForegroundApp()
                val screen = JarvisAccessibilityServiceRef.snapshot()
                if (command != null) {
                    JarvisRepository.pushFeedItem(FeedItem(text = "Você: $command", type = FeedItemType.INFO))
                }
                if (command != null) {
                    val long = AgentOrchestrator.isLong(command)
                    val lp = getSharedPreferences("giselle_long", MODE_PRIVATE)
                    if (long) lp.edit().putString("pending", command).apply()
                    orchestrator.processTask(command, app, screen).invokeOnCompletion { cause ->
                        if (long && cause == null) lp.edit().remove("pending").apply()
                    }
                } else orchestrator.process(null, app, screen)
            }
            ACTION_SPEAK -> intent.getStringExtra(EXTRA_COMMAND)?.let { voiceEngine.speak(it) }
            ACTION_AUTONOMOUS_TICK -> {
                val app = currentForegroundApp()
                val screen = JarvisAccessibilityServiceRef.snapshot()
                orchestrator.process(null, app, screen)
            }
            ACTION_RESOLVE_CONFIRMATION -> {
                val approved = intent.getBooleanExtra(EXTRA_APPROVED, false)
                val createdAt = intent.getLongExtra(EXTRA_PENDING_CREATED_AT, -1L)
                val pending = JarvisRepository.pendingActions.value.find { it.createdAt == createdAt }
                if (pending != null) {
                    orchestrator.resolveConfirmation(pending, approved)
                    JarvisRepository.removePendingAction(pending)
                }
            }
            ACTION_REFRESH_MEMORIES -> {
                serviceScope.launch {
                    val all = memoryStore.recall(query = "", topK = 100)
                    JarvisRepository.setMemories(all)
                }
            }
        }
        return START_STICKY
    }

    private fun currentForegroundApp(): String =
        JarvisAccessibilityServiceRef.currentPackageName() ?: "desconhecido"

    private fun notifyConfirmationNeeded(pending: PendingAction) {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Giselle precisa confirmar")
            .setContentText(pending.decision.userFacingSummary)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java).notify(pending.hashCode(), notification)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Giselle")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Giselle Core", NotificationManager.IMPORTANCE_LOW)
            )
            manager.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL_ID, "Giselle Confirmações", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        voiceEngine.destroy()
        JarvisRepository.setServiceRunning(false)
        super.onDestroy()
    }
}
