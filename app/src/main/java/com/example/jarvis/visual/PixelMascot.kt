package com.example.jarvis.visual

import android.graphics.Canvas
import android.graphics.Paint
import com.example.jarvis.core.Face
import kotlin.math.sin

/** Mascote pixel-art original da Giselle: criatura redonda de orelhas pontudas, grelha 14 colunas. */
class PixelMascot(private val dp: Float) {
    private val body = arrayOf(
        "..DD......DD..",
        ".DBBD....DBBD.",
        ".DBEBDDDDBEBD.",
        ".DBBBBBBBBBBD.",
        "DBBBBBBBBBBBBD",
        "DBBBBBBBBBBBBD",
        "DBBBBBBBBBBBBD",
        "DBBBBBBBBBBBBD",
        "DBBLLLLLLLLBBD",
        ".DBLLLLLLLLBD.",
        ".DBBLLLLLLBBD.",
        "..DBBBBBBBBD..",
        "...DDDDDDDD..."
    )
    private val dark = 0xFF2A2350.toInt()
    private val light = 0xFFE4DAFF.toInt()
    private val ear = 0xFFFFA8C8.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val p = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }

    private fun bodyColor(f: Face) = when (f) {
        Face.SAD -> 0xFF8FA3B5.toInt()
        Face.SLEEPY -> 0xFF6F5BC4.toInt()
        Face.HAPPY, Face.LISTENING, Face.SPEAKING -> 0xFF9B7FFF.toInt()
        else -> 0xFF8B6CFF.toInt()
    }

    fun draw(c: Canvas, cx: Float, cy: Float, size: Float, t: Float, face: Face, pop: Float, sleeping: Boolean) {
        val f = if (sleeping) Face.SLEEPY else face
        val cell = size / 15f
        val bob = if (f == Face.SLEEPY) sin(t * 1.2f) * cell * 0.25f
                  else if (f == Face.SAD) 0f else sin(t * 2.4f) * cell * 0.4f
        val jump = if (f == Face.HAPPY) -kotlin.math.abs(sin(t * 6f)) * cell * 1.2f else 0f
        val x0 = cx - 7f * cell
        val y0 = cy - 6.5f * cell + bob + jump
        val sc = 1f + 0.18f * pop
        c.save(); c.scale(sc, sc, cx, cy)

        fun px(col: Int, row: Int, color: Int) {
            p.color = color
            val x = x0 + col * cell; val y = y0 + row * cell
            c.drawRect(x, y, x + cell + 0.6f, y + cell + 0.6f, p)
        }
        val bc = bodyColor(f)
        for ((r, line) in body.withIndex()) for ((col, ch) in line.withIndex()) when (ch) {
            'B' -> px(col, r, bc); 'D' -> px(col, r, dark); 'L' -> px(col, r, light); 'E' -> px(col, r, ear)
        }
        val blink = (t % 4f) > 3.85f
        fun eyeOpen(x: Int, tall: Int = 2, dx: Int = 0, dy: Int = 0) {
            for (j in 0 until tall) { px(x + dx, 5 + j + dy, dark); px(x + 1 + dx, 5 + j + dy, dark) }
            px(x + dx, 5 + dy, white)
        }
        fun eyeLine(x: Int) { px(x, 6, dark); px(x + 1, 6, dark) }
        fun eyeArch(x: Int) { px(x, 6, dark); px(x + 1, 5, dark); px(x + 2, 6, dark) }
        when {
            f == Face.SLEEPY || blink -> { eyeLine(3); eyeLine(9) }
            f == Face.HAPPY -> { eyeArch(3); eyeArch(8) }
            f == Face.WINK -> { eyeOpen(3); eyeLine(9) }
            f == Face.SAD -> { eyeOpen(3, 2, 0, 1); eyeOpen(9, 2, 0, 1); px(5, 8, 0xFF7CC8FF.toInt()) }
            f == Face.SURPRISED || f == Face.LISTENING -> { eyeOpen(3, 3); eyeOpen(9, 3) }
            f == Face.THINKING -> { eyeOpen(3, 2, 1, -1); eyeOpen(9, 2, 1, -1) }
            else -> { eyeOpen(3); eyeOpen(9) }
        }
        // boca
        val talking = f == Face.SPEAKING && (t * 8f).toInt() % 2 == 0
        when {
            f == Face.SAD -> { px(6, 9, dark); px(7, 9, dark); px(5, 10, dark); px(8, 10, dark) }
            f == Face.SURPRISED || talking -> { px(6, 9, dark); px(7, 9, dark); px(6, 10, dark); px(7, 10, dark) }
            f == Face.HAPPY -> { px(5, 9, dark); px(8, 9, dark); px(6, 10, dark); px(7, 10, dark) }
            f != Face.SLEEPY -> { px(6, 9, dark); px(7, 9, dark) }
        }
        // extras: "z" a dormir e "..." a pensar
        if (f == Face.SLEEPY) {
            val k = ((t * 1.2f) % 1f); p.color = white; p.alpha = (255 * (1f - k)).toInt()
            val zx = x0 + 11 * cell; val zy = y0 - cell * (0.5f + k * 1.5f)
            c.drawRect(zx, zy, zx + cell * 1.5f, zy + cell * 0.5f, p); p.alpha = 255
        }
        if (f == Face.THINKING) {
            val n = ((t * 3f).toInt() % 3) + 1
            for (i in 0 until n) px(10 + i * 1, -1, white)
        }
        c.restore()
    }
}
