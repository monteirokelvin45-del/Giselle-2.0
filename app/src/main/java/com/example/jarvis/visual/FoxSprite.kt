package com.example.jarvis.visual

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import com.example.jarvis.R
import com.example.jarvis.core.Face
import kotlin.math.abs
import kotlin.math.sin

/** Raposa "elle" (desenho do Kelvin) animada por transformações: respira, inclina, salta e adormece. */
class FoxSprite(ctx: Context) {
    private val bmp = BitmapFactory.decodeResource(ctx.resources, R.drawable.giselle_fox)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sad = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.25f) })
    private val drowsy = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.6f) })
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val dst = RectF()

    fun draw(c: Canvas, cx: Float, cy: Float, size: Float, t: Float, face: Face, pop: Float, sleeping: Boolean, walking: Boolean = false) {
        val f = if (sleeping) Face.SLEEPY else face
        val h = size * 0.96f
        val w = h * bmp.width / bmp.height
        val feetY = cy + h / 2f
        var rot = 0f; var sc = 1f + 0.12f * pop; var dy = 0f
        when (f) {
            Face.THINKING -> rot = sin(t * 2.2f) * 6f
            Face.LISTENING -> { sc *= 1.04f + 0.02f * sin(t * 7f); rot = sin(t * 5f) * 2f }
            Face.SPEAKING -> dy = -abs(sin(t * 9f)) * h * 0.035f
            Face.HAPPY -> { dy = -abs(sin(t * 6f)) * h * 0.12f; rot = sin(t * 6f) * 4f }
            Face.SAD -> { rot = -4f + sin(t * 18f) * 1.2f; sc *= 0.96f }
            Face.SURPRISED -> sc *= 1.10f
            Face.SLEEPY -> { rot = 5f; dy = sin(t * 1.2f) * h * 0.01f }
            else -> dy = sin(t * 2.2f) * h * 0.012f // respiração em repouso
        }
        if (walking) { rot = sin(t * 11f) * 5f; dy = -abs(sin(t * 11f)) * h * 0.045f } // passinhos
        paint.colorFilter = when (f) { Face.SAD -> sad; Face.SLEEPY -> drowsy; else -> null }
        paint.alpha = if (f == Face.SLEEPY) 190 else 255
        c.save()
        c.rotate(rot, cx, feetY)
        c.scale(sc, sc, cx, feetY)
        dst.set(cx - w / 2f, cy - h / 2f + dy, cx + w / 2f, cy + h / 2f + dy)
        c.drawBitmap(bmp, null, dst, paint)
        c.restore()
        // "..." a pensar e "z" a dormir, por cima da cabeça
        if (f == Face.THINKING) {
            val n = ((t * 3f).toInt() % 3) + 1
            for (i in 0 until n) c.drawCircle(cx + w * 0.30f + i * h * 0.05f, cy - h * 0.46f, h * 0.018f, dot)
        }
        if (f == Face.SLEEPY) {
            val k = (t * 1.2f) % 1f
            dot.alpha = (255 * (1f - k)).toInt()
            c.drawCircle(cx + w * 0.32f, cy - h * (0.40f + 0.12f * k), h * 0.02f, dot)
            dot.alpha = 255
        }
    }
}
