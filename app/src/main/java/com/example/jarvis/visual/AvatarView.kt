package com.example.jarvis.visual

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import com.example.jarvis.core.Face
import com.example.jarvis.core.GiselleBus
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Estados visuais da Giselle. Uma única máquina de estados governa a animação,
 * para que o que se vê nunca contradiga o que ela está a fazer.
 */
enum class AvatarState { REST, LISTENING, THINKING, ACTING, SPEAKING, HAPPY, SUCCESS, ERROR, SLEEP, YAWN }

/** Traduz as expressões antigas (Face) para os estados novos. */
internal fun Face.toAvatarState(): AvatarState = when (this) {
    Face.IDLE -> AvatarState.REST
    Face.LISTENING, Face.SURPRISED -> AvatarState.LISTENING
    Face.THINKING -> AvatarState.THINKING
    Face.SPEAKING -> AvatarState.SPEAKING
    Face.HAPPY, Face.WINK -> AvatarState.HAPPY
    Face.SAD -> AvatarState.ERROR
    Face.SLEEPY -> AvatarState.YAWN
}

/**
 * Raposa da Giselle (Canvas, sem Lottie): óculos escuros, camisola preta e cauda.
 * Desenhada em coordenadas de 200×200 unidades, escalada para a view.
 *
 * Estados: repouso (respira, pisca), ouvir (orelhas em pé, cauda rápida), pensar
 * (olhar para cima, anel roxo), agir (encolhe, anel azul), falar (boca segue o áudio),
 * sucesso (aceno e anel verde), erro (orelhas caídas, cores dessaturadas), dormir e bocejar.
 */
class AvatarView(ctx: Context) : View(ctx) {

    private var base = AvatarState.REST
    private var transientState: AvatarState? = null
    private var transientUntil = 0L
    private var winkUntil = 0L

    private var pulse = 0f
    private var squash = 0f

    /** Humor persistente (-5..5): influencia velocidade e cor do anel. */
    var mood = 0
    /** 0 = laranja padrão, 1 = vivo, 2 = escuro. */
    var skin = 0
    var sleeping = false
        private set

    private val startNanos = System.nanoTime()
    private var lastInteraction = startNanos
    private var lastFrame = startNanos
    private var nextBlink = startNanos + 3_000_000_000L
    private var blinkUntil = 0L
    private var nextIdle = startNanos + 6_000_000_000L
    private var gazeUntil = 0L
    private var tx = 0f
    private var ty = 0f
    private var gx = 0f
    private var gy = 0f
    private var scaleNow = 1f

    private val dp = ctx.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = OUTLINE
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 11f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val zLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9FA8DA.toInt()
        textSize = 9f
        isFakeBoldText = true
    }
    private val rect = RectF()
    private val path = Path()
    private val hsv = FloatArray(3)
    private val ticker = Handler(Looper.getMainLooper())

    private val idleTask = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            if (transientState != null && now >= transientUntil) transientState = null
            if (!sleeping && base == AvatarState.REST && transientState == null && now >= nextIdle) idleBehavior(now)
            if (isAttachedToWindow) ticker.postDelayed(this, 1000)
        }
    }

    init {
        contentDescription = "Giselle. Toque para falar; mantenha pressionado para dormir."
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    // ---------- API usada pelo OverlayController ----------

    /** Estado persistente até outra chamada. */
    fun setState(state: AvatarState) {
        base = state
        transientState = null
        touch()
        invalidate()
    }

    /** Compatibilidade com as expressões antigas. */
    fun setFace(f: Face) = setState(f.toAvatarState())

    /** Estado temporário que volta ao estado base após [ms]. */
    fun flash(state: AvatarState, ms: Long) {
        transientState = state
        transientUntil = System.nanoTime() + ms * 1_000_000L
        touch()
        invalidate()
    }

    fun react(f: Face, ms: Long) {
        if (f == Face.WINK) winkUntil = System.nanoTime() + ms * 1_000_000L
        flash(f.toAvatarState(), ms)
    }

    /** Pulso discreto (ex.: cada ação executada). */
    fun pulse() { pulse = 1f; invalidate() }

    /** Pequeno "amassar" ao toque. */
    fun poke() { squash = 1f; touch(); invalidate() }

    fun touch() { lastInteraction = System.nanoTime() }

    /** Olhar para (nx, ny) em -1..1 durante ~1,5 s. */
    fun lookAt(nx: Float, ny: Float) {
        tx = nx.coerceIn(-1f, 1f)
        ty = ny.coerceIn(-1f, 1f)
        gazeUntil = System.nanoTime() + 1_500_000_000L
    }

    fun setSleeping(b: Boolean) {
        sleeping = b
        touch()
        alpha = if (b) 0.65f else 1f
        invalidate()
    }

    // ---------- ciclo de vida ----------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ticker.removeCallbacks(idleTask)
        ticker.postDelayed(idleTask, 1000)
    }

    override fun onDetachedFromWindow() {
        ticker.removeCallbacks(idleTask)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // Ao voltar a ficar visível, retoma o loop de animação; enquanto oculta, ele para sozinho.
        if (visibility == VISIBLE) invalidate()
    }

    // ---------- lógica ----------

    private fun effectiveState(now: Long): AvatarState {
        if (sleeping) return AvatarState.SLEEP
        transientState?.let { if (now < transientUntil) return it }
        if (base == AvatarState.REST) {
            if (now - lastInteraction > 120_000_000_000L) return AvatarState.SLEEP
            if (mood >= 3) return AvatarState.HAPPY
        }
        return base
    }

    private fun idleBehavior(now: Long) {
        when (Random.nextInt(3)) {
            0 -> lookAt(Random.nextFloat() * 2 - 1, Random.nextFloat() * 1.2f - 0.6f)
            1 -> react(Face.WINK, 600)
            else -> react(Face.SLEEPY, 1400)
        }
        nextIdle = now + 6_000_000_000L + (Random.nextFloat() * 10_000_000_000L).toLong()
    }

    private fun targetScale(st: AvatarState): Float = if (st == AvatarState.ACTING) 0.72f else 1f

    // ---------- desenho ----------

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = ((now - lastFrame).coerceAtMost(100_000_000L) / 1_000_000_000f).coerceAtLeast(0f)
        lastFrame = now
        val t = ((now - startNanos).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()

        pulse *= 0.9f.pow(dt * 60f)
        squash *= 0.88f.pow(dt * 60f)
        if (now > gazeUntil) { tx = 0f; ty = 0f }
        val smoothing = (1f - exp(-dt * 12f)).coerceIn(0f, 1f)
        gx += (tx - gx) * smoothing
        gy += (ty - gy) * smoothing

        if (now >= nextBlink) {
            blinkUntil = now + 130_000_000L
            nextBlink = now + (3_000_000_000L + (Random.nextFloat() * 3_000_000_000f).toLong())
        }
        val blinking = now < blinkUntil

        val st = effectiveState(now)
        scaleNow += (targetScale(st) - scaleNow) * (1f - exp(-dt * 6f)).coerceIn(0f, 1f)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Erro treme; sucesso acena. Ambos são curtos e discretos.
        var dx = 0f
        var dy = 0f
        if (st == AvatarState.ERROR) dx = sin(t * 60f) * 1.6f
        if (st == AvatarState.SUCCESS) dy = -sin(t * 14f).coerceAtLeast(0f) * 3f

        val s = min(w, h) / 200f * 0.92f
        c.save()
        c.translate(w / 2f, h / 2f)
        c.scale(s, s)
        c.translate(-100f, -100f)

        drawAura(c, st, t)

        // Respiração e escala de estado, ancoradas nos pés (100, 165).
        val breath = if (st == AvatarState.SLEEP) 1f + 0.015f * sin(t * 2f * PI.toFloat() / 7f)
                     else 1f + 0.03f * sin(t * 2f * PI.toFloat() / 4f)
        val sx = scaleNow * breath * (1f + 0.2f * squash)
        val sy = scaleNow * breath * (1f - 0.2f * squash)
        c.save()
        c.translate(dx, dy)
        c.translate(100f, 165f)
        c.scale(sx, sy)
        c.translate(-100f, -165f)
        drawFox(c, st, t, blinking, now)
        c.restore()

        if (st == AvatarState.SLEEP) {
            // "z" a flutuar, no mesmo sistema de coordenadas da raposa.
            val k = (t * 0.6f) % 1f
            zLabel.alpha = ((1f - k) * 255f).toInt()
            c.drawText("z", 140f, 56f - k * 10f, zLabel)
        }
        c.restore()

        if (windowVisibility == VISIBLE && isAttachedToWindow && animationsEnabled()) {
            postInvalidateDelayed(if (sleeping) FRAME_MS_SLEEP else FRAME_MS)
        }
    }

    private fun drawAura(c: Canvas, st: AvatarState, t: Float) {
        val calm = mood <= -2
        val (color, spin, radius, alpha) = when (st) {
            AvatarState.REST, AvatarState.YAWN ->
                if (calm) Quad(0xFF5C7CFA.toInt(), 0.25f, 92f, 80)
                else Quad(0xFFFFB74D.toInt(), 0.45f, 92f, 90)
            AvatarState.LISTENING -> Quad(TEAL, 3f, 94f + 3f * sin(t * 6f), 210)
            AvatarState.THINKING -> Quad(PURPLE, -2.2f, 92f, 190)
            AvatarState.ACTING -> Quad(BLUE, 1.6f, 88f, 150)
            AvatarState.SPEAKING -> Quad(BLUE, 0.8f, 92f + 4f * GiselleBus.mouthOpen.coerceIn(0f, 1f), 210)
            AvatarState.HAPPY, AvatarState.SUCCESS -> Quad(TEAL, 1.2f, 92f, 220)
            AvatarState.ERROR -> Quad(0xFF90A4AE.toInt(), 0f, 92f, 120)
            AvatarState.SLEEP -> return
        }
        val r = radius + 6f * pulse
        rect.set(100f - r, 100f - r, 100f + r, 100f + r)
        stroke.strokeWidth = 2.6f
        stroke.color = (alpha shl 24) or (color and 0xFFFFFF)
        val angle = t * spin * 57.3f
        val arcs = if (st == AvatarState.THINKING) 2 else 1
        for (i in 0 until arcs) {
            c.drawArc(rect, angle + i * 180f, 110f, false, stroke)
        }
        stroke.color = OUTLINE
    }

    private fun drawFox(c: Canvas, st: AvatarState, t: Float, blinking: Boolean, now: Long) {
        val body = applySkin(bodyBase(st))
        val tailMul = (1f + mood * 0.08f).coerceIn(0.6f, 1.6f)
        val wagSpeed = when (st) {
            AvatarState.LISTENING -> 7f
            AvatarState.THINKING -> 1.2f
            AvatarState.SPEAKING -> 2.5f
            AvatarState.HAPPY, AvatarState.SUCCESS -> 5f
            AvatarState.ACTING -> 3f
            AvatarState.ERROR, AvatarState.SLEEP -> 0.4f
            else -> 1.6f
        } * tailMul
        val wagAmp = when (st) {
            AvatarState.LISTENING -> 14f
            AvatarState.HAPPY, AvatarState.SUCCESS -> 16f
            AvatarState.ERROR -> -6f
            AvatarState.SLEEP -> 2f
            AvatarState.REST, AvatarState.YAWN -> 3f
            else -> 6f
        }
        val earAngle = when (st) {
            AvatarState.ERROR -> 14f
            AvatarState.LISTENING -> -4f + 3f * sin(t * 9f)
            else -> 0f
        }

        // Cauda (atrás do corpo), balança em torno da base.
        c.save()
        c.rotate(sin(t * wagSpeed) * wagAmp, 134f, 132f)
        drawTail(c, body)
        c.restore()

        // Orelhas (esquerda e espelhada à direita), atrás da cabeça.
        c.save()
        c.rotate(-earAngle, 66f, 62f)
        drawEar(c, body)
        c.restore()
        c.save()
        c.scale(-1f, 1f, 100f, 0f)
        c.rotate(-earAngle, 66f, 62f)
        drawEar(c, body)
        c.restore()

        // Corpo, barriga, feet e camisola.
        fill.color = body
        c.drawOval(rect.apply { set(60f, 92f, 140f, 166f) }, fill)
        c.drawOval(rect.apply { set(72f, 152f, 98f, 170f) }, fill)
        c.drawOval(rect.apply { set(102f, 152f, 128f, 170f) }, fill)
        c.drawOval(rect.apply { set(60f, 92f, 140f, 166f) }, stroke)
        fill.color = WHITE
        c.drawOval(rect.apply { set(84f, 134f, 116f, 156f) }, fill)

        fill.color = if (st == AvatarState.ERROR) desaturate(SHIRT, 0.2f) else SHIRT
        rect.set(60f, 96f, 140f, 144f)
        c.drawRoundRect(rect, 10f, 10f, fill)
        c.drawRoundRect(rect, 10f, 10f, stroke)
        c.drawText(SHIRT_TEXT, 100f, 127f, label)

        // Braços: nos lados da camisola. Ao agir, o direito "acena".
        drawArm(c, body)
        c.save()
        c.scale(-1f, 1f, 100f, 0f)
        if (st == AvatarState.ACTING || st == AvatarState.SUCCESS) {
            c.rotate(-30f + 4f * sin(t * 6f), 136f, 112f)
        }
        drawArm(c, body)
        c.restore()

        // Cabeça e máscara branca do focinho.
        fill.color = body
        rect.set(58f, 26f, 142f, 100f)
        c.drawOval(rect, fill)
        c.drawOval(rect, stroke)
        fill.color = WHITE
        rect.set(70f, 74f, 130f, 102f)
        c.drawOval(rect, fill)

        drawFace(c, st, t, blinking, now)
    }

    private fun drawFace(c: Canvas, st: AvatarState, t: Float, blinking: Boolean, now: Long) {
        // Óculos: hastes, ponte e lentes. As lentes escondem os olhos; o brilho segue o olhar.
        val winking = now < winkUntil && st == AvatarState.HAPPY
        val closedAll = blinking || st == AvatarState.SLEEP || st == AvatarState.YAWN
        val gazeUpBias = if (st == AvatarState.THINKING) -0.5f else 0f

        stroke.strokeWidth = 2.2f
        c.drawLine(58f, 68f, 70f, 68f, stroke)
        c.drawLine(130f, 68f, 142f, 68f, stroke)
        c.drawLine(95f, 67f, 105f, 67f, stroke)

        drawLens(c, 83f, 68f, closedAll, gazeUpBias)
        drawLens(c, 117f, 68f, closedAll || winking, gazeUpBias)

        // Nariz.
        fill.color = OUTLINE
        rect.set(96f, 76f, 103f, 82f)
        c.drawOval(rect, fill)

        // Boca conforme o estado.
        stroke.strokeWidth = 1.8f
        when (st) {
            AvatarState.SPEAKING -> {
                val open = 1.5f + 8f * GiselleBus.mouthOpen.coerceIn(0f, 1f)
                fill.color = OUTLINE
                rect.set(94.5f, 88f - open / 2f, 103.5f, 88f + open / 2f)
                c.drawOval(rect, fill)
            }
            AvatarState.YAWN -> {
                fill.color = OUTLINE
                rect.set(95f, 85f, 103f, 96f)
                c.drawOval(rect, fill)
            }
            AvatarState.HAPPY, AvatarState.SUCCESS -> {
                path.reset()
                path.moveTo(91f, 86f)
                path.quadTo(99f, 96f, 107f, 86f)
                c.drawPath(path, stroke)
            }
            AvatarState.ERROR -> {
                path.reset()
                path.moveTo(93f, 93f)
                path.quadTo(99f, 87f, 105f, 93f)
                c.drawPath(path, stroke)
            }
            else -> {
                path.reset()
                path.moveTo(93f, 87f)
                path.quadTo(96f, 90f, 99f, 87f)
                path.quadTo(102f, 90f, 105f, 87f)
                c.drawPath(path, stroke)
            }
        }
    }

    private fun drawLens(c: Canvas, cx: Float, cy: Float, closed: Boolean, gazeUpBias: Float) {
        fill.color = OUTLINE
        val hh = if (closed) 1.5f else LENS_R
        rect.set(cx - LENS_R, cy - hh, cx + LENS_R, cy + hh)
        c.drawOval(rect, fill)
        if (!closed) {
            fill.color = 0xCCFFFFFF.toInt()
            c.drawCircle(cx - 3.5f + gx * 3f, cy - 3.5f + (gy + gazeUpBias) * 3f, 2.2f, fill)
        }
    }

    private fun drawTail(c: Canvas, body: Int) {
        path.reset()
        path.moveTo(132f, 138f)
        path.cubicTo(160f, 146f, 184f, 128f, 178f, 96f)
        path.cubicTo(176f, 90f, 168f, 90f, 164f, 98f)
        path.cubicTo(156f, 116f, 146f, 126f, 128f, 124f)
        path.close()
        fill.color = body
        c.drawPath(path, fill)
        c.save()
        c.clipPath(path)
        fill.color = WHITE
        c.drawCircle(176f, 100f, 9f, fill)
        c.restore()
        c.drawPath(path, stroke)
    }

    private fun drawEar(c: Canvas, body: Int) {
        path.reset()
        path.moveTo(58f, 66f)
        path.quadTo(52f, 36f, 64f, 18f)
        path.quadTo(80f, 28f, 90f, 46f)
        path.lineTo(86f, 68f)
        path.close()
        fill.color = body
        c.drawPath(path, fill)
        c.drawPath(path, stroke)
        path.reset()
        path.moveTo(66f, 56f)
        path.quadTo(63f, 38f, 67f, 30f)
        path.quadTo(78f, 38f, 82f, 50f)
        path.close()
        fill.color = WHITE
        c.drawPath(path, fill)
    }

    private fun drawArm(c: Canvas, body: Int) {
        rect.set(48f, 106f, 70f, 138f)
        fill.color = body
        c.drawOval(rect, fill)
        c.drawOval(rect, stroke)
    }

    // ---------- cores ----------

    private fun bodyBase(st: AvatarState): Int = when (st) {
        AvatarState.ERROR -> desaturate(ORANGE, 0.5f)
        AvatarState.SLEEP -> desaturate(ORANGE, 0.35f)
        else -> ORANGE
    }

    private fun desaturate(color: Int, amount: Float): Int {
        Color.colorToHSV(color, hsv)
        hsv[1] *= (1f - amount)
        return Color.HSVToColor(hsv)
    }

    private fun applySkin(color: Int): Int {
        if (skin == 0) return color
        Color.colorToHSV(color, hsv)
        if (skin == 1) {
            hsv[1] = 1f
            hsv[2] = 1f
        } else {
            hsv[1] = (hsv[1] * 0.8f).coerceAtMost(1f)
            hsv[2] *= 0.55f
        }
        return Color.HSVToColor(hsv)
    }

    private fun animationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    private data class Quad(val color: Int, val spin: Float, val radius: Float, val alpha: Int)

    companion object {
        private const val OUTLINE = 0xFF10131A.toInt()
        private const val ORANGE = 0xFFF0853A.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val SHIRT = 0xFF14161C.toInt()
        private const val TEAL = 0xFF00E5C8.toInt()
        private const val PURPLE = 0xFF8E5BFF.toInt()
        private const val BLUE = 0xFF2979FF.toInt()
        private const val LENS_R = 12.5f
        private const val FRAME_MS = 33L      // ~30 fps
        private const val FRAME_MS_SLEEP = 66L // ~15 fps ao dormir
        /** Texto da camisola. */
        const val SHIRT_TEXT = "elle"
    }
}
