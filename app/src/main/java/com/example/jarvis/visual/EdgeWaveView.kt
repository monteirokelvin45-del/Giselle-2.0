package com.example.jarvis.visual

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.view.View
import com.example.jarvis.core.GiselleState
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** Ondas animadas nas 4 bordas; cor e ritmo mudam por estado. Só redesenha enquanto não-IDLE. */
class EdgeWaveView(ctx: Context) : View(ctx) {
    private var state = GiselleState.IDLE
    private var pulse = 0f
    private var errorStart = 0L
    private val animationStartNanos = System.nanoTime()
    private var lastFrameNanos = animationStartNanos
    private val dp = ctx.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    init { setLayerType(View.LAYER_TYPE_HARDWARE, null) }

    fun setState(s: GiselleState) {
        state = s
        if (s == GiselleState.ERROR) errorStart = System.nanoTime()
        invalidate()
    }

    fun pulse() { pulse = 1f; invalidate() }

    private fun baseColor(): Int = when (state) {
        GiselleState.LISTENING -> 0xFF00E5C8.toInt()
        GiselleState.THINKING -> 0xFF8E5BFF.toInt()
        GiselleState.EXECUTING -> 0xFFFFB300.toInt()
        GiselleState.SPEAKING -> 0xFF2979FF.toInt()
        GiselleState.ERROR -> 0xFFFF3B30.toInt()
        GiselleState.IDLE -> 0
    }

    override fun onDraw(c: Canvas) {
        if (state == GiselleState.IDLE) return
        val now = System.nanoTime()
        val dt = ((now - lastFrameNanos).coerceAtMost(100_000_000L) / 1_000_000_000f).coerceAtLeast(0f)
        lastFrameNanos = now
        val t = ((now - animationStartNanos).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()
        pulse *= 0.93f.pow(dt * 60f)
        var amp = 0f
        var speed = 0f
        when (state) {
            GiselleState.LISTENING -> { amp = 12f + 14f * pulse; speed = 1.2f }
            GiselleState.THINKING -> { amp = 10f; speed = 4.5f }
            GiselleState.EXECUTING -> { amp = 12f; speed = -2.5f }
            GiselleState.SPEAKING -> { amp = 10f + 26f * pulse; speed = 2f }
            GiselleState.ERROR -> {
                val k = (now - errorStart) / 1e9f
                amp = if (k < 1.2f) 30f * max(0f, sin(k * 2f * PI.toFloat() / 0.6f)) else 0f
                speed = 3f
            }
            else -> {}
        }
        if (amp <= 0f) return
        val w = width.toFloat(); val h = height.toFloat()
        c.save(); edge(c, w, amp, speed, t); c.restore()
        c.save(); c.translate(0f, h); c.scale(1f, -1f); edge(c, w, amp, speed, t + 1f); c.restore()
        c.save(); c.translate(0f, h); c.rotate(-90f); edge(c, h, amp, speed, t + 2f); c.restore()
        c.save(); c.translate(w, 0f); c.rotate(90f); edge(c, h, amp, speed, t + 3f); c.restore()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()) {
            postInvalidateOnAnimation()
        }
    }

    private fun edge(c: Canvas, len: Float, amp: Float, speed: Float, t: Float) {
        val color = baseColor() and 0x00FFFFFF
        for (i in 0 until 3) {
            val a = amp * dp * (1f - i * 0.25f)
            val ph = t * speed + i * 1.7f
            path.reset(); path.moveTo(0f, 0f)
            var x = 0f
            while (x <= len) {
                path.lineTo(x, a * (0.55f + 0.45f * sin(x / (60f * dp) * (1f + i * 0.4f) + ph)))
                x += 12f * dp
            }
            path.lineTo(len, 0f); path.close()
            paint.color = color or ((110 - i * 30) shl 24)
            c.drawPath(path, paint)
        }
    }
}
