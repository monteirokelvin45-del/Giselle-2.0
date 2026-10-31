package com.example.jarvis.visual

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import com.example.jarvis.core.Face
import com.example.jarvis.core.GiselleBus
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Avatar vivo da Giselle (Canvas): respira, pisca, olha em volta, pisca o olho, boceja,
 * segue o dedo com o olhar, "amassa" ao toque e adormece. 9 expressões.
 */
class AvatarView(ctx: Context) : View(ctx) {
    private var face = Face.IDLE
    private var override: Face? = null
    private var overrideUntil = 0L
    private var pulse = 0f
    private var squash = 0f
    var mood = 0
    var skin = 0
    var sleeping = false
        private set
    private val animationStartNanos = System.nanoTime()
    private var lastInteraction = animationStartNanos
    private var nextIdle = animationStartNanos + 6_000_000_000L
    private var gx = 0f; private var gy = 0f; private var tx = 0f; private var ty = 0f
    private var gazeUntil = 0L
    private var lastFrameNanos = animationStartNanos
    private val idleHandler = Handler(Looper.getMainLooper())
    private val hsv = FloatArray(3)
    private var lastColorFace: Face? = null
    private var fromColor = 0
    private var targetColor = 0
    private var displayedColor = 0
    private var colorTransitionStart = 0L
    private val idleTask = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            if (override != null && now >= overrideUntil) override = null
            if (!sleeping && face == Face.IDLE && now >= nextIdle && override == null) idleBehavior(now)
            invalidate()
            if (isAttachedToWindow) idleHandler.postDelayed(this, 1000)
        }
    }

    private val dp = ctx.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = DARK; strokeWidth = 3f * dp
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DARK; textSize = 13f * dp; isFakeBoldText = true }
    private val oval = RectF()

    companion object { private const val DARK = 0xFF10131A.toInt() }

    init {
        contentDescription = "Avatar Giselle. Toque para falar; mantenha pressionado para dormir."
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setFace(f: Face) { face = f; touch(); invalidate() }
    fun pulse() { pulse = 1f; invalidate() }
    fun poke() { squash = 1f; touch(); invalidate() }
    fun touch() { lastInteraction = System.nanoTime() }

    /** Olhar para (nx, ny) em -1..1 durante ~1,5 s. */
    fun lookAt(nx: Float, ny: Float) {
        tx = nx.coerceIn(-1f, 1f); ty = ny.coerceIn(-1f, 1f)
        gazeUntil = System.nanoTime() + 1_500_000_000L
    }

    /** Expressão temporária (feliz, triste, surpresa…). */
    fun react(f: Face, ms: Long) {
        override = f; overrideUntil = System.nanoTime() + ms * 1_000_000L
        touch(); invalidate()
    }

    fun setSleeping(b: Boolean) {
        sleeping = b; touch(); alpha = if (b) 0.65f else 1f; invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        idleHandler.removeCallbacks(idleTask)
        idleHandler.postDelayed(idleTask, 1000)
    }

    override fun onDetachedFromWindow() {
        idleHandler.removeCallbacks(idleTask)
        super.onDetachedFromWindow()
    }

    private fun effective(now: Long): Face = when {
        sleeping -> Face.SLEEPY
        override != null && now < overrideUntil -> override ?: face
        face == Face.IDLE && now - lastInteraction > 120_000_000_000L -> Face.SLEEPY
        face == Face.IDLE && mood >= 3 -> Face.HAPPY
        face == Face.IDLE && mood <= -3 -> Face.SAD
        else -> face
    }

    private fun body(f: Face): Int {
        val c = baseBody(f)
        if (skin == 0) return c
        android.graphics.Color.colorToHSV(c, hsv)
        if (skin == 1) { hsv[1] = 1f; hsv[2] = 1f } else { hsv[1] = (hsv[1] * 0.8f).coerceAtMost(1f); hsv[2] *= 0.55f }
        return android.graphics.Color.HSVToColor(hsv)
    }

    private fun baseBody(f: Face): Int = when (f) {
        Face.LISTENING -> 0xFF00E5C8.toInt()
        Face.THINKING -> 0xFF8E5BFF.toInt()
        Face.SPEAKING -> 0xFF2979FF.toInt()
        Face.HAPPY, Face.WINK -> 0xFFFFD54F.toInt()
        Face.SAD -> 0xFF90A4AE.toInt()
        Face.SURPRISED -> 0xFFFF8A65.toInt()
        Face.SLEEPY -> 0xFF9FA8DA.toInt()
        Face.IDLE -> 0xFFB0BEC5.toInt()
    }

    /** Vida própria quando parada: olha em volta, pisca o olho, boceja. */
    private fun idleBehavior(now: Long) {
        when (Random.nextInt(3)) {
            0 -> lookAt(Random.nextFloat() * 2 - 1, Random.nextFloat() * 1.2f - 0.6f)
            1 -> react(Face.WINK, 600)
            else -> react(Face.SLEEPY, 1400)
        }
        nextIdle = now + 6_000_000_000L + (Random.nextFloat() * 10_000_000_000L).toLong()
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNanos).coerceAtMost(100_000_000L) / 1_000_000_000f).coerceAtLeast(0f)
        lastFrameNanos = now
        val t = ((now - animationStartNanos).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()
        pulse *= 0.9f.pow(dt * 60f)
        squash *= 0.88f.pow(dt * 60f)
        if (now > gazeUntil) { tx = 0f; ty = 0f }
        val smoothing = (1f - exp(-dt * 12f)).coerceIn(0f, 1f)
        gx += (tx - gx) * smoothing; gy += (ty - gy) * smoothing
        val f = effective(now)

        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2; val cy = h / 2; val r = minOf(w, h) / 2 - 4 * dp
        val breathe = if (sleeping) 1f else 1f + 0.02f * sin(t * 2f)
        c.save()
        c.scale(breathe * (1f + 0.2f * squash), breathe * (1f - 0.2f * squash), cx, cy)

        fill.color = transitionedBody(f, now); c.drawCircle(cx, cy, r, fill)

        // olhos
        val blink = (t % 4f) > 3.88f
        val ew = when (f) { Face.SURPRISED -> 7.5f; Face.LISTENING -> 6.5f; else -> 5.5f } * dp
        val ey = cy - r * 0.2f
        for (eye in 0..1) {
            val s = if (eye == 0) -1f else 1f
            val ex = cx + s * r * 0.36f
            when {
                f == Face.SLEEPY || blink -> c.drawLine(ex - ew, ey, ex + ew, ey, line)
                f == Face.WINK && s > 0 -> c.drawLine(ex - ew, ey, ex + ew, ey, line)
                f == Face.HAPPY -> { oval.set(ex - ew, ey - ew, ex + ew, ey + ew); c.drawArc(oval, 200f, 140f, false, line) }
                else -> {
                    fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(ex, ey, ew, fill)
                    val up = if (f == Face.THINKING) -0.45f else 0f
                    fill.color = DARK
                    c.drawCircle(ex + gx * ew * 0.45f, ey + (gy + up) * ew * 0.45f, ew * 0.52f, fill)
                }
            }
        }

        // boca
        val my = cy + r * 0.38f
        fill.color = DARK
        when (f) {
            Face.SPEAKING -> {
                val open = (2f + 17f * GiselleBus.mouthOpen.coerceIn(0f, 1f)) * dp
                oval.set(cx - 9f * dp, my - open / 2, cx + 9f * dp, my + open / 2); c.drawOval(oval, fill)
            }
            Face.HAPPY, Face.WINK -> { oval.set(cx - 12f * dp, my - 10f * dp, cx + 12f * dp, my + 8f * dp); c.drawArc(oval, 10f, 160f, false, line) }
            Face.SAD -> { oval.set(cx - 10f * dp, my, cx + 10f * dp, my + 14f * dp); c.drawArc(oval, 200f, 140f, false, line) }
            Face.SURPRISED -> c.drawCircle(cx, my + 2f * dp, 5f * dp, fill)
            Face.THINKING -> for (i in 0 until 3) {
                val a = (sin(t * 5f - i) + 1f) / 2f
                c.drawCircle(cx + (i - 1) * 9f * dp, my, (2f + 2f * a) * dp, fill)
            }
            Face.SLEEPY -> c.drawLine(cx - 5f * dp, my, cx + 5f * dp, my, line)
            else -> c.drawLine(cx - 8f * dp, my, cx + 8f * dp, my, line)
        }
        c.restore()

        // "z z" a flutuar quando dorme
        if (f == Face.SLEEPY) {
            val k = if (sleeping) 0f else (t * 1.5f) % 1f
            c.drawText("z", cx + r * 0.55f, cy - r * 0.55f - k * 8f * dp, text)
        }

        if (!sleeping) {
            val busy = f == Face.SPEAKING || f == Face.THINKING || f == Face.LISTENING ||
                squash > 0.02f || pulse > 0.02f || abs(tx - gx) > 0.02f || abs(ty - gy) > 0.02f
            if (animationsEnabled()) {
                if (busy) postInvalidateOnAnimation() else postInvalidateDelayed(200) // idle ~5 fps
            }
        }
    }

    private fun animationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    private fun transitionedBody(f: Face, now: Long): Int {
        val next = body(f)
        if (lastColorFace != f || targetColor != next) {
            fromColor = if (lastColorFace == null) next else displayedColor
            targetColor = next
            lastColorFace = f
            colorTransitionStart = now
        }
        val amount = ((now - colorTransitionStart).coerceAtLeast(0L) / 240_000_000f).coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = fromColor ushr shift and 0xff
            val b = targetColor ushr shift and 0xff
            return (a + (b - a) * amount).toInt().coerceIn(0, 255)
        }
        displayedColor = Color.argb(channel(24), channel(16), channel(8), channel(0))
        return displayedColor
    }
}
