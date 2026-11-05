// ui/ElleMascote.kt — mascote "elle" na tela inicial (sprites em res/drawable-nodpi).
package com.example.jarvis.ui

import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.jarvis.BuildConfig
import com.example.jarvis.R
import com.example.jarvis.core.GiselleState
import com.example.jarvis.mascote.ElleMaquina
import com.example.jarvis.mascote.PoseElle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

@DrawableRes
private fun recursoDaPose(pose: PoseElle): Int = when (pose) {
    PoseElle.PARADO -> R.drawable.elle_parado
    PoseElle.PISCANDO -> R.drawable.elle_piscando
    PoseElle.FALANDO -> R.drawable.elle_falando
    PoseElle.DORMINDO -> R.drawable.elle_dormindo
}

private fun agoraMs(): Long = SystemClock.uptimeMillis()

/** Tempo sem interação até dormir: 3 s em debug (teste), 40 s na versão final. */
private val TEMPO_SONO_MS: Long = if (BuildConfig.DEBUG) 3_000L else 40_000L

/**
 * Mascote "elle". Acompanha o estado da Giselle:
 *  - falando (SPEAKING) → boca alterna com a pose parada, junto com a voz;
 *  - pensando, ouvindo ou executando → pose parada com piscadas mais rápidas, e não dorme;
 *  - ocioso → parado, piscando a cada 2,5 s; dorme após [TEMPO_SONO_MS] sem interação;
 *  - toque ou [interacao] (ex.: digitação) → acorda e pula.
 */
@Composable
fun ElleMascote(
    estadoGiselle: GiselleState,
    tamanho: Dp,
    modifier: Modifier = Modifier,
    interacao: Any? = null,
) {
    val maquina = remember { ElleMaquina(tempoSono = TEMPO_SONO_MS) }
    var pose by remember { mutableStateOf(PoseElle.PARADO) }
    val pulo = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Relógio da máquina: ~25 atualizações por segundo (a boca precisa de 150 ms por quadro).
    LaunchedEffect(Unit) {
        maquina.iniciar(agoraMs())
        while (true) {
            pose = maquina.pose(agoraMs())
            delay(40)
        }
    }

    // Estado da Giselle.
    LaunchedEffect(estadoGiselle) {
        val agora = agoraMs()
        when (estadoGiselle) {
            GiselleState.SPEAKING -> {
                maquina.pensando(false, agora)
                maquina.falar(true, agora)
            }
            GiselleState.LISTENING, GiselleState.THINKING, GiselleState.EXECUTING -> {
                maquina.falar(false, agora)
                maquina.tocar(agora)
                maquina.pensando(true, agora)
            }
            else -> {
                maquina.falar(false, agora)
                maquina.pensando(false, agora)
            }
        }
        pose = maquina.pose(agora)
    }

    // Interação do usuário (digitação, por exemplo): acorda o mascote.
    LaunchedEffect(interacao) {
        maquina.tocar(agoraMs())
        pose = maquina.pose(agoraMs())
    }

    Box(
        modifier
            .size(tamanho)
            .graphicsLayer {
                // Pulo de reação ao toque: sobe e volta em ~380 ms.
                translationY = -sin(pulo.value * PI).toFloat() * 16.dp.toPx()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                maquina.tocar(agoraMs())
                pose = maquina.pose(agoraMs())
                scope.launch {
                    pulo.snapTo(0f)
                    pulo.animateTo(1f, tween(380))
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Crossfade curto: a boca alterna a cada 150 ms e precisa de troca rápida.
        Crossfade(
            targetState = pose,
            animationSpec = tween(durationMillis = 90),
            label = "pose-elle",
        ) { p ->
            Image(
                painter = painterResource(recursoDaPose(p)),
                contentDescription = when (p) {
                    PoseElle.DORMINDO -> "Elle dormindo"
                    PoseElle.FALANDO -> "Elle falando"
                    else -> "Elle"
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
