// ============================================================
// ui/MainActivity.kt
// ------------------------------------------------------------
// Tela única com 3 abas: Comando (chat + voz), Confirmações
// pendentes, e Memórias aprendidas. É por aqui que você
// efetivamente USA o Jarvis.
// ============================================================

package com.example.jarvis.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Shapes
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.example.jarvis.R
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.jarvis.agent.PendingAction
import com.example.jarvis.BuildConfig
import com.example.jarvis.core.FeedItem
import com.example.jarvis.core.FeedItemType
import com.example.jarvis.core.JarvisForegroundService
import com.example.jarvis.core.JarvisRepository
import com.example.jarvis.core.MemoryEntry
import com.example.jarvis.llm.TrackerInsightClient
import com.example.jarvis.llm.ProxyAuth
import com.example.jarvis.core.CircuitBreaker
import com.example.jarvis.core.RateLimiter
import com.example.jarvis.tracker.agent.TrackerFeedback
import com.example.jarvis.tracker.agent.TrackerOrchestrator
import com.example.jarvis.tracker.agent.TrackerRepository
import com.example.jarvis.tracker.data.TrackerDatabase
import com.example.jarvis.tracker.data.BackupManager
import com.example.jarvis.tracker.onboarding.OnboardingScreen
import com.example.jarvis.tracker.onboarding.OnboardingViewModelFactory
import com.example.jarvis.tracker.ui.TrackerScreen
import com.example.jarvis.voice.VoiceEngine
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var voiceEngine: VoiceEngine
    private lateinit var trackerOrchestrator: TrackerOrchestrator

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* resultado tratado no fluxo normal da tela */ }
    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(::exportBackupTo) }
    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::importBackupFrom) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceEngine = VoiceEngine(applicationContext).apply { init() }

        requestNeededPermissions()
        ensureServiceStarted()
        setupTrackerOrchestrator()

        // Item 6: se detectado "Jarvis" em background, aciona escuta
        // de comando automaticamente — sem precisar tocar no microfone.
        com.example.jarvis.voice.WakeWordService.onWakeWordDetected = {
            runOnUiThread {
                voiceEngine.listenOnce(
                    onResult = { text -> sendCommand(text) },
                    onError = { }
                )
            }
        }

        setContent {
            MaterialTheme {
                var onboardingDone by remember { mutableStateOf<Boolean?>(null) }
                var trackerMoeda by remember { mutableStateOf("MZN") }

                LaunchedEffect(Unit) {
                    val perfil = TrackerDatabase.getInstance(applicationContext).configPerfilDao().get()
                    onboardingDone = perfil?.status == "confirmado"
                    trackerMoeda = perfil?.moeda ?: "MZN"
                }

                when (onboardingDone) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    false -> OnboardingScreen(
                        factory = OnboardingViewModelFactory(TrackerDatabase.getInstance(applicationContext)),
                        onFinished = {
                            onboardingDone = true
                            lifecycleScope.launch {
                                trackerMoeda = TrackerDatabase.getInstance(applicationContext)
                                    .configPerfilDao().get()?.moeda ?: "MZN"
                            }
                        }
                    )
                    true -> JarvisApp(
                        voiceEngine = voiceEngine,
                        isAccessibilityEnabled = { isAccessibilityServiceEnabled() },
                        onOpenAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onSendCommand = { command -> sendCommand(command) },
                        onResolveConfirmation = { pending, approved -> resolveConfirmation(pending, approved) },
                        onRefreshMemories = { refreshMemories() },
                        onGenerateInsight = {
                            TrackerRepository.setStatusMessage("Analisando seus dados...")
                            trackerOrchestrator.generateInsight()
                        },
                        onResolveInsightFeedback = { eventId, feedback ->
                            TrackerRepository.markResolvedLocally(eventId, feedback.name.lowercase())
                            trackerOrchestrator.resolveInsightFeedback(eventId, feedback)
                        },
                        onExportBackup = { exportBackupLauncher.launch("jarvis-backup.json") },
                        onImportBackup = { importBackupLauncher.launch(arrayOf("application/json", "text/json")) },
                        registroFisicoDao = TrackerDatabase.getInstance(applicationContext).registroFisicoDao(),
                        registroFinanceiroDao = TrackerDatabase.getInstance(applicationContext).registroFinanceiroDao(),
                        moedaPadrao = trackerMoeda
                    )
                }
            }
        }
    }

    private fun setupTrackerOrchestrator() {
        val db = TrackerDatabase.getInstance(applicationContext)
        val insightClient = TrackerInsightClient(
            proxyBaseUrl = BuildConfig.PROXY_BASE_URL.ifBlank { null },
            proxySharedSecret = BuildConfig.PROXY_SHARED_SECRET.ifBlank { null },
            proxyClientId = ProxyAuth.clientId(applicationContext)
        )

        trackerOrchestrator = TrackerOrchestrator(
            claude = insightClient,
            configPerfilDao = db.configPerfilDao(),
            metasDao = db.metasDao(),
            registroFisicoDao = db.registroFisicoDao(),
            registroMentalDao = db.registroMentalDao(),
            registroEstudoDao = db.registroEstudoDao(),
            registroFinanceiroDao = db.registroFinanceiroDao(),
            aiEventDao = db.aiEventDao(),
            rateLimiter = RateLimiter(maxCallsPerWindow = 10, windowMs = 60_000L),
            circuitBreaker = CircuitBreaker(failureThreshold = 3, openDurationMs = 60_000L),
            onInsightGenerated = { event ->
                runOnUiThread {
                    TrackerRepository.setStatusMessage(null)
                    TrackerRepository.addInsight(event)
                }
            },
            onCircuitOpen = { reason ->
                runOnUiThread { TrackerRepository.setStatusMessage(reason) }
            },
            onInsufficientData = { reason ->
                runOnUiThread { TrackerRepository.setStatusMessage(reason) }
            }
        )
    }

    private fun exportBackupTo(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                val file = BackupManager(applicationContext, TrackerDatabase.getInstance(applicationContext))
                    .exportarBackup("manual")
                val output = contentResolver.openOutputStream(uri) ?: error("Não foi possível gravar o backup.")
                output.use { stream -> file.inputStream().use { it.copyTo(stream) } }
            }.onSuccess {
                Toast.makeText(this@MainActivity, "Backup exportado.", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@MainActivity, "Falha ao exportar backup.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun importBackupFrom(uri: Uri) {
        lifecycleScope.launch {
            val temp = File.createTempFile("jarvis-restore-", ".json", cacheDir)
            runCatching {
                val input = contentResolver.openInputStream(uri) ?: error("Não foi possível ler o arquivo.")
                input.use { source ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= 64L * 1024 * 1024) { "Arquivo demasiado grande." }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                BackupManager(applicationContext, TrackerDatabase.getInstance(applicationContext))
                    .restaurarBackup(temp)
            }.onSuccess { result ->
                Toast.makeText(
                    this@MainActivity,
                    "Backup mesclado: ${result.insertedRecords} registros novos.",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure {
                Toast.makeText(this@MainActivity, "Backup inválido; nenhum dado foi alterado.", Toast.LENGTH_LONG).show()
            }
            temp.delete()
        }
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS
        )
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private val projectionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { r ->
        r.data?.let { com.example.jarvis.vision.ProjectionService.start(this, r.resultCode, it) }
    }

    private fun startVoiceIfPermitted() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            ContextCompat.startForegroundService(this, Intent(this, com.example.jarvis.voice.WakeWordService::class.java))
        }
    }

    // Android < 11 não tem screenshot por acessibilidade: pede captura de ecrã (1x por sessão).
    private fun requestVisionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < 30 && !com.example.jarvis.vision.ProjectionService.running) {
            val mpm = getSystemService(android.media.projection.MediaProjectionManager::class.java)
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }
    }

    override fun onResume() {
        super.onResume()
        startVoiceIfPermitted()
    }

    private fun ensureServiceStarted() {
        val intent = Intent(this, JarvisForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        startVoiceIfPermitted()
        requestVisionIfNeeded()
    }

    private fun sendCommand(command: String) {
        val intent = Intent(this, JarvisForegroundService::class.java).apply {
            action = JarvisForegroundService.ACTION_USER_COMMAND
            putExtra(JarvisForegroundService.EXTRA_COMMAND, command)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun resolveConfirmation(pending: PendingAction, approved: Boolean) {
        val intent = Intent(this, JarvisForegroundService::class.java).apply {
            action = JarvisForegroundService.ACTION_RESOLVE_CONFIRMATION
            putExtra(JarvisForegroundService.EXTRA_APPROVED, approved)
            putExtra(JarvisForegroundService.EXTRA_PENDING_CREATED_AT, pending.createdAt)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun refreshMemories() {
        val intent = Intent(this, JarvisForegroundService::class.java).apply {
            action = JarvisForegroundService.ACTION_REFRESH_MEMORIES
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "${packageName}/${packageName}.accessibility.JarvisAccessibilityService"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(expected)
    }

    override fun onDestroy() {
        voiceEngine.destroy()
        super.onDestroy()
    }
}

// ============================================================
// COMPOSABLES
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisApp(
    voiceEngine: VoiceEngine,
    isAccessibilityEnabled: () -> Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onSendCommand: (String) -> Unit,
    onResolveConfirmation: (PendingAction, Boolean) -> Unit,
    onRefreshMemories: () -> Unit,
    onGenerateInsight: () -> Unit,
    onResolveInsightFeedback: (Long, TrackerFeedback) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    registroFisicoDao: com.example.jarvis.tracker.data.RegistroFisicoDao,
    registroFinanceiroDao: com.example.jarvis.tracker.data.RegistroFinanceiroDao,
    moedaPadrao: String
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val pendingActions by JarvisRepository.pendingActions.collectAsStateWithLifecycle()
    val accessibilityOn = remember { isAccessibilityEnabled() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun irPara(tab: Int) {
        selectedTab = tab
        if (tab == 2) onRefreshMemories()
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.97f),
                drawerContainerColor = Color(0xFF111111),
            ) {
                Text(
                    "Giselle",
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif),
                    color = Color(0xFFEDEDED),
                    modifier = Modifier.padding(start = 24.dp, top = 28.dp, bottom = 20.dp),
                )
                ITENS_MENU.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.rotulo, style = MaterialTheme.typography.titleMedium) },
                        icon = { Icon(item.icone, contentDescription = null) },
                        selected = false,
                        onClick = { irPara(item.aba) },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color(0xFFD8D8D8),
                            unselectedIconColor = Color(0xFFD8D8D8),
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = Color(0xFF2A2A2A),
                )
                Text(
                    "Fixados",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF8A8A8A),
                    modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 12.dp),
                )
                Text(
                    "Recentes",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF8A8A8A),
                    modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.avatar_perfil),
                        contentDescription = "Perfil",
                        modifier = Modifier.size(44.dp).clip(CircleShape),
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { irPara(Abas.INICIO) },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF7F7F5),
                            contentColor = Color(0xFF111111),
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nova conversa", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                // A tela inicial desenha o próprio cabeçalho; as demais usam esta barra.
                if (selectedTab != 0) {
                    TopAppBar(
                        title = { Text(rotuloDaAba(selectedTab), fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Abrir menu")
                            }
                        },
                        actions = {
                            if (pendingActions.isNotEmpty()) {
                                Badge(containerColor = Color(0xFFE53935)) { Text("${pendingActions.size}") }
                                Spacer(Modifier.width(12.dp))
                            }
                        }
                    )
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (!accessibilityOn) {
                    AccessibilityWarningBanner(onOpenAccessibilitySettings)
                }
                when (selectedTab) {
                    0 -> HomeScreen(
                        voiceEngine = voiceEngine,
                        onSendCommand = onSendCommand,
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        onQuickNav = { tab -> irPara(tab) },
                        modifier = Modifier.weight(1f)
                    )
                    1 -> ConfirmationsScreen(pendingActions, onResolveConfirmation)
                    2 -> MemoriesScreen()
                    3 -> com.example.jarvis.tracker.ui.TrackerScreen(
                        onGenerateInsight = onGenerateInsight,
                        onResolveFeedback = onResolveInsightFeedback
                    )
                    4 -> RegistrarTabScreen(registroFisicoDao, registroFinanceiroDao, moedaPadrao)
                    5 -> com.example.jarvis.updates.UpdatesScreen()
                    Abas.CODE -> {
                        val vm: com.example.jarvis.selfupdate.ui.PatchReviewViewModel = viewModel(
                            factory = com.example.jarvis.selfupdate.ui.PatchReviewViewModelFactory(context)
                        )
                        com.example.jarvis.selfupdate.ui.PatchReviewScreen(vm, context)
                    }
                    Abas.ARTEFATOS -> ArtefatosScreen(context)
                    Abas.DEFINICOES -> DefinicoesScreen(
                        onAtualizacoes = { irPara(Abas.ATUALIZACOES) },
                        onExportarBackup = onExportBackup,
                        onRestaurarBackup = onImportBackup,
                        onAcessibilidade = onOpenAccessibilitySettings,
                    )
                }
            }
        }
    }
}

private fun rotuloDaAba(tab: Int): String = when (tab) {
    0 -> "Início"
    1 -> "Confirmações"
    2 -> "Memórias"
    3 -> "Tracker"
    4 -> "Registrar"
    5 -> "Atualizações"
    Abas.CODE -> "Code"
    Abas.ARTEFATOS -> "Artefatos"
    Abas.DEFINICOES -> "Definições"
    else -> "Giselle"
}

@Composable
fun RegistrarTabScreen(
    registroFisicoDao: com.example.jarvis.tracker.data.RegistroFisicoDao,
    registroFinanceiroDao: com.example.jarvis.tracker.data.RegistroFinanceiroDao,
    moedaPadrao: String
) {
    var subTab by remember { mutableIntStateOf(0) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Físico") })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Financeiro") })
        }

        savedMessage?.let {
            Surface(color = Color(0xFFE8F5E9), modifier = Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(12.dp))
            }
        }

        when (subTab) {
            0 -> com.example.jarvis.tracker.ui.RegistroFisicoScreen(
                dao = registroFisicoDao,
                onSaved = { savedMessage = "Registro físico salvo." }
            )
            1 -> com.example.jarvis.tracker.ui.RegistroFinanceiroScreen(
                dao = registroFinanceiroDao,
                moedaPadrao = moedaPadrao,
                onSaved = { savedMessage = "Transação salva." }
            )
        }
    }
}

@Composable
fun AccessibilityWarningBanner(onOpenSettings: () -> Unit) {
    Surface(color = Color(0xFFFFF3CD), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Acessibilidade desativada — a Giselle não consegue ler nem agir na tela ainda.",
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenSettings) { Text("Ativar") }
        }
    }
}

@Composable
fun FeedBubble(item: FeedItem) {
    val (bg, label) = when (item.type) {
        FeedItemType.INFO -> Color(0xFFE3F2FD) to null
        FeedItemType.ACTION_DONE -> Color(0xFFE8F5E9) to "✓ ação executada"
        FeedItemType.ACTION_FAILED -> Color(0xFFFFEBEE) to "✗ falhou"
        FeedItemType.CONFIRMATION_ASKED -> Color(0xFFFFF3E0) to "⏸ aguardando confirmação"
        FeedItemType.SPOKEN -> Color(0xFFF3E5F5) to null
    }
    val time = remember(item.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }

    Surface(color = bg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Text(item.text)
            Text(time, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun ConfirmationsScreen(
    pending: List<PendingAction>,
    onResolve: (PendingAction, Boolean) -> Unit
) {
    if (pending.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhuma confirmação pendente.", color = Color.Gray)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(pending) { action ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(action.app, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    Text(action.decision.userFacingSummary, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onResolve(action, true) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) { Text("Confirmar") }
                        OutlinedButton(onClick = { onResolve(action, false) }) { Text("Rejeitar") }
                    }
                }
            }
        }
    }
}

@Composable
fun MemoriesScreen() {
    val memories by JarvisRepository.memories.collectAsStateWithLifecycle()

    if (memories.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Ainda não há memórias registradas.", color = Color.Gray)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(memories) { memory -> MemoryCard(memory) }
    }
}

@Composable
fun MemoryCard(memory: MemoryEntry) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(memory.category.name) })
                AssistChip(onClick = {}, label = { Text("conf. ${"%.0f".format(memory.confidence * 100)}%") })
            }
            Spacer(Modifier.height(6.dp))
            Text(memory.content)
        }
    }
}

private data class ItemMenu(val rotulo: String, val icone: ImageVector, val aba: Int)

/** Itens do menu lateral, na ordem da referência. */
private val ITENS_MENU = listOf(
    ItemMenu("Conversas", Icons.Outlined.Forum, Abas.INICIO),
    ItemMenu("Cofre", Icons.Outlined.Inventory2, Abas.MEMORIAS),
    ItemMenu("Code", Icons.Filled.Code, Abas.CODE),
    ItemMenu("Artefatos", Icons.Outlined.Inventory2, Abas.ARTEFATOS),
    ItemMenu("Definições", Icons.Outlined.Shapes, Abas.DEFINICOES),
)
