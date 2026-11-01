package com.example.jarvis.visual

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.example.jarvis.core.Face
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Orbe de anéis luminosos da Giselle (original: violeta + turquesa).
 * Desenhado só com Path e traços translúcidos (sem blur), barato para o overlay.
 * O estado (Face) muda velocidade, amplitude, brilho e cor, com transição suave.
 */
class OrbRenderer(private val dp: Float) {
    private class Look(
        val speed: Float, val amp: Float, val alpha: Float,
        val color: Int, val jitter: Float = 0f, val scale: Float = 1f
    )

    private val violet = 0xFF8B6CFF.toInt()
    private val turquoise = 0xFF35E0C8.toInt()
    private val danger = 0xFFFF5A6E.toInt()

    private fun look(f: Face) = when (f) {
        Face.LISTENING -> Look(1.3f, 0.16f, 1.00f, turquoise)
        Face.THINKING -> Look(2.6f, 0.09f, 0.95f, violet)
        Face.SPEAKING -> Look(1.7f, 0.13f, 1.00f, turquoise)
        Face.HAPPY -> Look(1.2f, 0.10f, 1.00f, turquoise, scale = 1.08f)
        Face.SAD -> Look(3.2f, 0.05f, 0.90f, danger, jitter = 0.035f, scale = 0.92f)
        Face.SURPRISED -> Look(2.0f, 0.12f, 1.00f, turquoise, scale = 1.12f)
        Face.SLEEPY -> Look(0.25f, 0.03f, 0.35f, violet, scale = 0.85f)
        else -> Look(0.45f, 0.05f, 0.60f, violet) // IDLE / WINK
    }

    private var speed = 0.45f; private var amp = 0.05f; private var alpha = 0.6f
    private var jit = 0f; private var scl = 1f
    private var r = 139f; private var g = 108f; private var b = 255f
    private var phase = 0f

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    fun draw(c: Canvas, cx: Float, cy: Float, radius: Float, t: Float, dt: Float,
             face: Face, pop: Float, sleeping: Boolean) {
        val tgt = look(if (sleeping) Face.SLEEPY else face)
        val k = (1f - exp(-dt * 6f)).coerceIn(0f, 1f)
        speed += (tgt.speed - speed) * k; amp += (tgt.amp - amp) * k
        alpha += (tgt.alpha - alpha) * k; jit += (tgt.jitter - jit) * k
        scl += (tgt.scale - scl) * k
        r += (((tgt.color shr 16) and 0xff) - r) * k
        g += (((tgt.color shr 8) and 0xff) - g) * k
        b += ((tgt.color and 0xff) - b) * k
        phase += dt * speed * 2f * PI.toFloat()

        val base = radius * 0.62f * scl * (1f + 0.25f * pop)
        val rgb = (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
        val steps = 56
        val rings = 5
        for (i in 0 until rings) {
            val o = i * 1.7f
            val wob = amp * (1f + 0.5f * sin(phase * 0.5f + o))
            path.rewind()
            for (s in 0..steps) {
                val a = (s.toFloat() / steps) * 2f * PI.toFloat()
                val m = wob * sin(a * (2 + i % 3) + phase + o) +
                    wob * 0.6f * cos(a * 3f - phase * 0.7f + o) +
                    jit * sin(a * 9f + t * 40f + i)
                val rr = base * (1f + m)
                val x = cx + rr * cos(a); val y = cy + rr * sin(a)
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            // brilho: traço largo e fraco por baixo, fio fino e nítido por cima
            ring.color = (((alpha * 0.16f * 255).toInt()) shl 24) or rgb
            ring.strokeWidth = 5.5f * dp; c.drawPath(path, ring)
            ring.color = (((alpha * (0.55f + 0.08f * i) * 255).toInt().coerceAtMost(255)) shl 24) or rgb
            ring.strokeWidth = 1.4f * dp; c.drawPath(path, ring)
        }
        // núcleo pequeno que respira
        core.color = (((alpha * 0.9f * 255).toInt()) shl 24) or rgb
        c.drawCircle(cx, cy, radius * (0.07f + 0.012f * sin(phase * 1.5f) + 0.05f * pop), core)
    }
}
