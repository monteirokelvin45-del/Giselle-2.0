package com.example.jarvis.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import com.example.jarvis.voice.VoiceSelection
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jarvis.core.FeedItem
import com.example.jarvis.core.GiselleBus
import com.example.jarvis.core.GiselleState
import com.example.jarvis.core.JarvisRepository
import com.example.jarvis.voice.VoiceEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalTime
import kotlin.coroutines.resume

// Cores da tela inicial (tema escuro fixo, como na referência).
private val CorFundo = Color(0xFF131313)
private val CorCaixa = Color(0xFF1F1F23)
private val Hint = Color(0xFFB7B7BE)          // contraste ~8:1 sobre CorCaixa
private val OrbIdle = Color(0xFF2979FF)
private val OrbListening = Color(0xFF00E5C8)
private val OrbThinking = Color(0xFF8E5BFF)
private val OrbSpeaking = Color(0xFF40C4FF)
private val OrbError = Color(0xFFFF5252)

private val HomeScheme = darkColorScheme(
    primary = OrbIdle,
    background = CorFundo,
    surface = CorCaixa,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Hint,
    surfaceVariant = CorCaixa,
)

/** Preferência de UI: nome exibido na saudação (vazio = saudação sem nome). */
const val PREFS_UI = "giselle_ui"
const val PREF_NOME = "nome_usuario"

/**
 * Tela inicial: saudação, orbe de estado e caixa de comando.
 *
 * Papéis dos botões (cada um faz exatamente o que o rótulo diz):
 *  - microfone: DITA para o campo de texto (o usuário revê e envia);
 *  - onda: CONVERSA por voz contínua (ouve, envia, ouve de novo, até ser desligada);
 *  - ghost: MODO PRIVADO, oculta a conversa nesta tela.
 */
@Composable
fun HomeScreen(
    voiceEngine: VoiceEngine,
    onSendCommand: (String) -> Unit,
    onOpenMenu: () -> Unit,
    onQuickNav: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    MaterialTheme(colorScheme = HomeScheme) {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE) }
        val nome = remember { prefs.getString(PREF_NOME, "").orEmpty() }

        val gState by GiselleBus.state.collectAsStateWithLifecycle()
        val feed by JarvisRepository.feed.collectAsStateWithLifecycle()

        var privado by rememberSaveable { mutableStateOf(false) }
        var mostrarDicaPrivado by remember { mutableStateOf(false) }
        var texto by rememberSaveable { mutableStateOf("") }
        var ditando by remember { mutableStateOf(false) }
        var conversa by remember { mutableStateOf(false) }
        var turno by remember { mutableIntStateOf(0) }
        var errosSeguidos by remember { mutableIntStateOf(0) }
        var avisoVoz by remember { mutableStateOf<String?>(null) }
        var menuRapido by remember { mutableStateOf(false) }

        LaunchedEffect(mostrarDicaPrivado) {
            if (mostrarDicaPrivado) {
                delay(4000)
                mostrarDicaPrivado = false
            }
        }

        // Conversa por voz contínua: ouve -> envia -> espera a Giselle terminar -> ouve de novo.
        LaunchedEffect(conversa, turno) {
            if (!conversa) return@LaunchedEffect
            if (turno > 0) delay(400)
            GiselleBus.state.first { it == GiselleState.IDLE || it == GiselleState.ERROR }
            val fala = ouvirUmaVez(voiceEngine) { erro -> avisoVoz = erro }
            if (fala.isNullOrBlank()) {
                errosSeguidos++
                if (errosSeguidos >= 3) {
                    conversa = false
                    errosSeguidos = 0
                    avisoVoz = "Conversa por voz pausada: não ouvi nada útil. Toque na onda para retomar."
                    return@LaunchedEffect
                }
            } else {
                errosSeguidos = 0
                avisoVoz = null
                onSendCommand(fala)
            }
            turno++
        }

        fun enviar() {
            val t = texto.trim()
            if (t.isNotEmpty()) {
                onSendCommand(t)
                texto = ""
            }
        }

        fun alternarConversa() {
            if (conversa) {
                conversa = false
                voiceEngine.cancelListening()
                avisoVoz = null
            } else {
                ditando = false
                errosSeguidos = 0
                avisoVoz = null
                conversa = true
                turno = 0
            }
        }

        fun ditar() {
            if (conversa || ditando) return
            ditando = true
            voiceEngine.listenOnce(
                onResult = { fala ->
                    ditando = false
                    texto = if (texto.isBlank()) fala else "${texto.trimEnd()} $fala"
                },
                onError = { erro -> ditando = false; avisoVoz = erro }
            )
        }

        val mostrarConversa = !privado && feed.isNotEmpty()
        val estadoOrbe = if (conversa && gState == GiselleState.IDLE) GiselleState.LISTENING else gState

        Column(
            modifier
                .fillMaxWidth()
                .background(CorFundo)
                .systemBarsPadding()
                .imePadding()
        ) {
            // Cabeçalho: menu à esquerda, modo privado à direita.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Filled.Menu, contentDescription = "Abrir menu")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    if (!privado) mostrarDicaPrivado = true
                    privado = !privado
                }) {
                    Icon(
                        GhostIcon,
                        contentDescription = if (privado) "Desligar modo privado" else "Ligar modo privado"
                    )
                }
            }

            // Rótulo do modo privado: aparece no primeiro toque e enquanto estiver ligado.
            AnimatedVisibility(visible = mostrarDicaPrivado || privado) {
                Text(
                    text = if (privado) "Modo privado ativo: a conversa fica oculta nesta tela."
                    else "Modo privado: oculta a conversa nesta tela. Toque de novo para desligar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Hint,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

            if (mostrarConversa) {
                // Com conversa: orbe pequeno no topo e histórico abaixo.
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    GiselleOrb(state = estadoOrbe, diameter = 96.dp)
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    reverseLayout = true
                ) {
                    items(feed.reversed()) { item: FeedItem -> FeedBubble(item) }
                }
            } else {
                // Sem conversa: orbe um pouco acima do centro, maior, com a saudação.
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(Modifier.weight(0.8f))
                    GiselleOrb(state = estadoOrbe, diameter = 220.dp)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = saudacao(nome),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                    Spacer(Modifier.weight(1f))
                }
            }

            avisoVoz?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Hint,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

            // Caixa de comando: uma linha, cresce só com o texto.
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = CorCaixa,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box {
                        IconButton(onClick = { menuRapido = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Atalhos")
                        }
                        DropdownMenu(expanded = menuRapido, onDismissRequest = { menuRapido = false }) {
                            listOf(1 to "Confirmações", 2 to "Memórias", 3 to "Tracker", 4 to "Registrar")
                                .forEach { (idx, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { menuRapido = false; onQuickNav(idx) }
                                    )
                                }
                        }
                    }

                    TextField(
                        value = texto,
                        onValueChange = { texto = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                when {
                                    ditando -> "Ouvindo…"
                                    conversa -> "Conversa por voz ativa"
                                    else -> "Escreva ou dite uma mensagem"
                                }
                            )
                        },
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { enviar() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedPlaceholderColor = Hint,
                            unfocusedPlaceholderColor = Hint,
                        ),
                    )

                    if (texto.isNotBlank()) {
                        IconButton(onClick = { enviar() }) {
                            Icon(Icons.Filled.Send, contentDescription = "Enviar")
                        }
                    } else {
                        IconButton(onClick = { ditar() }, enabled = !conversa) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = "Ditar mensagem",
                                tint = if (ditando) OrbError else Color.White,
                            )
                        }
                        // Onda: conversa por voz contínua (círculo preenchido quando ativa).
                        IconButton(
                            onClick = { alternarConversa() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(if (conversa) Color.White else Color.Transparent, CircleShape),
                        ) {
                            Icon(
                                Icons.Filled.GraphicEq,
                                contentDescription = if (conversa) "Encerrar conversa por voz" else "Iniciar conversa por voz",
                                tint = if (conversa) CorFundo else Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Uma escuta: devolve o texto reconhecido, ou null se nada útil foi ouvido. */
private suspend fun ouvirUmaVez(voiceEngine: VoiceEngine, onErro: (String) -> Unit): String? =
    suspendCancellableCoroutine { cont ->
        voiceEngine.listenOnce(
            onResult = { if (cont.isActive) cont.resume(it) },
            onError = { erro -> onErro(erro); if (cont.isActive) cont.resume(null) },
        )
        cont.invokeOnCancellation { voiceEngine.cancelListening() }
    }

private fun saudacao(nome: String): String {
    val hora = LocalTime.now().hour
    val base = when (hora) {
        in 5..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }
    return if (nome.isBlank()) base else "$base, $nome"
}

/**
 * Orbe de estado. A rotação é uma fase acumulada por frame (withFrameNanos), e a
 * velocidade transiciona suavemente entre estados, então trocar de estado nunca dá salto.
 */
@Composable
fun GiselleOrb(state: GiselleState, diameter: Dp, modifier: Modifier = Modifier) {
    val velocidadeAlvo = when (state) {
        GiselleState.LISTENING -> 150f
        GiselleState.THINKING, GiselleState.EXECUTING -> 240f
        GiselleState.SPEAKING -> 120f
        GiselleState.ERROR -> 25f
        GiselleState.IDLE -> 45f
    }
    val velocidade by animateFloatAsState(
        targetValue = velocidadeAlvo,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "velocidadeOrbe",
    )
    val cor by animateColorAsState(
        targetValue = when (state) {
            GiselleState.LISTENING -> OrbListening
            GiselleState.THINKING, GiselleState.EXECUTING -> OrbThinking
            GiselleState.SPEAKING -> OrbSpeaking
            GiselleState.ERROR -> OrbError
            GiselleState.IDLE -> OrbIdle
        },
        animationSpec = tween(600),
        label = "corOrbe",
    )
    val respiracao by rememberInfiniteTransition(label = "respiracaoOrbe").animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "respiracao",
    )

    var fase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var ultimo = -1L
        while (true) {
            withFrameNanos { agora ->
                if (ultimo >= 0L) fase = (fase + (agora - ultimo) / 1_000_000_000f * velocidade) % 360f
                ultimo = agora
            }
        }
    }

    Canvas(modifier.size(diameter).graphicsLayer { scaleX = respiracao; scaleY = respiracao }) {
        val rotacao = fase // lido na fase de desenho: não recompõe a cada frame
        val centro = Offset(size.width / 2f, size.height / 2f)
        val raio = size.minDimension / 2f

        // aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(cor.copy(alpha = 0.5f), cor.copy(alpha = 0.1f), Color.Transparent),
                center = centro,
                radius = raio,
            ),
            radius = raio,
            center = centro,
        )
        // núcleo com brilho
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.35f), cor.copy(alpha = 0.9f)),
                center = Offset(centro.x - raio * 0.12f, centro.y - raio * 0.15f),
                radius = raio * 0.46f,
            ),
            radius = raio * 0.42f,
            center = centro,
        )
        // três órbitas elípticas em inclinações diferentes, girando em sentidos alternados
        val orbitas = listOf(
            Triple(0.52f, 1f, 0.9f),
            Triple(0.78f, -0.7f, 0.7f),
            Triple(0.64f, 0.5f, 0.55f),
        )
        orbitas.forEachIndexed { i, (altura, sentido, opacidade) ->
            withTransform({ rotate(degrees = rotacao * sentido + i * 60f, pivot = centro) }) {
                drawOval(
                    color = cor.copy(alpha = opacidade),
                    topLeft = Offset(centro.x - raio * 0.94f, centro.y - raio * altura / 2f),
                    size = Size(raio * 1.88f, raio * altura),
                    style = Stroke(width = raio * 0.022f),
                )
                // ponto luminoso que percorre a órbita
                drawCircle(
                    color = Color.White,
                    radius = raio * 0.035f,
                    center = Offset(centro.x + raio * 0.94f, centro.y),
                )
            }
        }
    }
}

/** Ícone de fantasma (modo privado), desenhado como vetor. */
private val GhostIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Ghost",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(
            pathData = addPathNodes(
                "M12 3C8.1 3 5 6.1 5 10V20L7.5 18.5L10 20L12 18.5L14 20L16.5 18.5L19 20V10C19 6.1 15.9 3 12 3Z"
            ),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineJoin = StrokeJoin.Round,
        )
        .addPath(
            pathData = addPathNodes("M9.5 10.5H9.51M14.5 10.5H14.51"),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round,
        )
        .build()
}
