package com.example.jarvis.visual

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.example.jarvis.core.Face
import com.example.jarvis.core.GiselleBus
import com.example.jarvis.core.GiselleState
import com.example.jarvis.voice.WakeWordService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

/**
 * Overlay via TYPE_ACCESSIBILITY_OVERLAY (sem "sobrepor a outros apps").
 * - Ondas: só existem enquanto a Giselle está ativa.
 * - Avatar: sempre presente (pequeno e leve); toque = falar, 3 toques = cócegas,
 *   segurar = dormir/acordar, arrastar = mover (encosta à borda), olhos seguem o dedo.
 * - Balão de fala mostra o que ela diz.
 */
object OverlayController {
    private const val PREFS = "giselle_avatar"

    private var svc: AccessibilityService? = null
    private var wm: WindowManager? = null
    private var waves: EdgeWaveView? = null
    private var avatar: AvatarView? = null
    private var avatarLp: WindowManager.LayoutParams? = null
    private var bubble: TextView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var scope: CoroutineScope? = null
    private var hideJob: Job? = null
    private var bubbleJob: Job? = null
    private var snapAnimator: ValueAnimator? = null
    private var wanderAnim: ValueAnimator? = null
    private var curState = GiselleState.IDLE
    private var touching = false
    private val wanderTick = Runnable { startWalk() }
    private var greeted = false
    private var batteryRegistered = false
    private val handler = Handler(Looper.getMainLooper())
    private val taps = ArrayDeque<Long>()

    private val battery = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                Intent.ACTION_BATTERY_LOW -> { avatar?.react(Face.SAD, 4000); bubble("Bateria baixa… liga o carregador?", 4000) }
                Intent.ACTION_POWER_CONNECTED -> { avatar?.react(Face.HAPPY, 3000); bubble("Boa, energia! 🔋", 2500) }
            }
        }
    }

    private fun dp(v: Float) = (v * (svc?.resources?.displayMetrics?.density ?: 3f)).toInt()
    private fun prefs() = svc?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun size(asleep: Boolean) = dp(if (asleep) 44f else 96f)
    private fun safeBounds(): Rect {
        val service = svc ?: return Rect()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm?.currentWindowMetrics
            if (metrics != null) {
                val bounds = Rect(metrics.bounds)
                val insets = metrics.windowInsets.getInsets(
                    android.view.WindowInsets.Type.systemBars() or
                        android.view.WindowInsets.Type.displayCutout() or
                        android.view.WindowInsets.Type.ime()
                )
                bounds.left += insets.left
                bounds.top += insets.top
                bounds.right -= insets.right
                bounds.bottom -= insets.bottom
                val margin = dp(8f)
                bounds.inset(margin, margin)
                return bounds
            }
        }
        val dm = service.resources.displayMetrics
        return Rect(dp(8f), dp(8f), dm.widthPixels - dp(8f), dm.heightPixels - dp(8f))
    }

    fun attach(service: AccessibilityService) {
        // Android pode reconectar a mesma instância depois de recriar a janela
        // de acessibilidade; descarte views antigas mesmo quando o objeto é igual.
        svc?.let { detach(it) }
        svc = service
        wm = service.getSystemService(WindowManager::class.java)
        scope?.cancel()
        val sc = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = sc
        sc.launch { GiselleBus.stateEvents.collect { onState(it) } }
        sc.launch { GiselleBus.pulses.collect { waves?.pulse(); avatar?.pulse() } }
        sc.launch { GiselleBus.toast.collect { f -> avatar?.react(f, 3000); bumpMood(if (f == Face.HAPPY) 1 else -1) } }
        sc.launch { GiselleBus.notified.collect { avatar?.react(Face.SURPRISED, 1500); bubble(it, 3500) } }
        sc.launch { GiselleBus.announce.collect { avatar?.react(Face.SURPRISED, 2500) } }
        sc.launch { GiselleBus.speech.collect { bubble(it, (2500 + it.length * 55L).coerceAtMost(9000)) } }
        runCatching {
            ContextCompat.registerReceiver(
                service, battery,
                IntentFilter().apply { addAction(Intent.ACTION_BATTERY_LOW); addAction(Intent.ACTION_POWER_CONNECTED) },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            batteryRegistered = true
        }
        ensureAvatar()
        if (!greeted) {
            greeted = true
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            avatar?.react(Face.WINK, 1200)
            if (prefs()?.getBoolean("asleep", false) != true) {
                bubble(if (h in 5..11) "Bom dia!" else if (h in 12..17) "Boa tarde!" else "Boa noite!", 2500)
            }
        }
    }

    fun detach(service: AccessibilityService) {
        if (svc !== service) return
        handler.removeCallbacksAndMessages(null)
        wanderAnim?.cancel(); wanderAnim = null
        snapAnimator?.cancel()
        snapAnimator = null
        hideJob?.cancel(); hideJob = null
        bubbleJob?.cancel(); bubbleJob = null
        scope?.cancel(); scope = null
        val oldWm = wm
        listOfNotNull(bubble, waves, avatar).forEach { view ->
            runCatching { oldWm?.removeViewImmediate(view) }
        }
        if (batteryRegistered) {
            runCatching { service.unregisterReceiver(battery) }
            batteryRegistered = false
        }
        bubble = null; bubbleLp = null
        waves = null
        avatar = null; avatarLp = null
        wm = null
        svc = null
    }

    fun onConfigurationChanged() {
        val s = svc ?: return
        stopWalk()
        snapAnimator?.cancel()
        snapAnimator = null
        val a = avatar ?: return
        val lp = avatarLp ?: return
        val safe = safeBounds()
        val newSize = size(a.sleeping)
        lp.width = newSize; lp.height = newSize
        lp.x = lp.x.coerceIn(safe.left, (safe.right - newSize).coerceAtLeast(safe.left))
        lp.y = lp.y.coerceIn(safe.top, (safe.bottom - newSize).coerceAtLeast(safe.top))
        runCatching { wm?.updateViewLayout(a, lp) }
        placeBubble()
    }

    // ---------------- estados ----------------
    private fun onState(s: GiselleState) {
        hideJob?.cancel()
        curState = s
        if (s != GiselleState.IDLE) stopWalk() else scheduleWander(3000, 9000)
        if (s != GiselleState.IDLE && avatar?.sleeping == true) setSleeping(false)
        when (s) {
            GiselleState.LISTENING -> avatar?.setFace(Face.LISTENING)
            GiselleState.THINKING, GiselleState.EXECUTING -> avatar?.setFace(Face.THINKING)
            GiselleState.SPEAKING -> avatar?.setFace(Face.SPEAKING)
            GiselleState.ERROR -> avatar?.setFace(Face.SAD)
            GiselleState.IDLE -> avatar?.setFace(Face.IDLE)
        }
        // durante a execução o avatar fica translúcido e não intercepta toques
        setInteractive(s != GiselleState.EXECUTING)
        if (s == GiselleState.IDLE) {
            hideJob = scope?.launch { delay(600); removeWaves() }
        } else {
            addWaves(); waves?.setState(s)
        }
    }

    private fun setInteractive(on: Boolean) {
        val v = avatar ?: return; val lp = avatarLp ?: return
        lp.flags = if (on) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        v.alpha = if (on) (if (v.sleeping) 0.65f else 1f) else 0.35f
        runCatching { wm?.updateViewLayout(v, lp) }
    }

    // ---------------- ondas ----------------
    private fun addWaves() {
        if (waves != null) return
        val s = svc ?: return
        val v = EdgeWaveView(s)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (runCatching { wm?.addView(v, lp) }.isSuccess) {
            waves = v
        }
    }

    private fun removeWaves() {
        waves?.let { runCatching { wm?.removeView(it) } }
        waves = null
    }

    // ---------------- avatar ----------------

    // ---- Passear livremente pelo ecrã (só em repouso; pára ao falar, ouvir, executar, tocar ou dormir)
    private fun wanderEnabled() = prefs()?.getBoolean("wander", true) != false

    private fun scheduleWander(minMs: Long = 5000, maxMs: Long = 14000) {
        handler.removeCallbacks(wanderTick)
        if (!wanderEnabled()) return
        handler.postDelayed(wanderTick, minMs + (Math.random() * (maxMs - minMs)).toLong())
    }

    private fun stopWalk() {
        handler.removeCallbacks(wanderTick)
        wanderAnim?.cancel(); wanderAnim = null
        avatar?.walking = false
    }

    fun setWander(on: Boolean) {
        prefs()?.edit()?.putBoolean("wander", on)?.apply()
        if (on) scheduleWander(1500, 3000) else stopWalk()
    }

    private fun startWalk() {
        val v = avatar ?: return
        val lp = avatarLp ?: return
        if (!wanderEnabled() || curState != GiselleState.IDLE || v.sleeping || touching || wanderAnim != null) {
            scheduleWander(); return
        }
        val safe = safeBounds()
        val maxX = (safe.right - lp.width).coerceAtLeast(safe.left)
        val maxY = (safe.bottom - lp.height).coerceAtLeast(safe.top)
        val tx = (lp.x + ((Math.random() * 2 - 1) * safe.width() * 0.6).toInt()).coerceIn(safe.left, maxX)
        val ty = (lp.y + ((Math.random() * 2 - 1) * safe.height() * 0.25).toInt()).coerceIn(safe.top, maxY)
        val sx = lp.x; val sy = lp.y
        val dist = Math.hypot((tx - sx).toDouble(), (ty - sy).toDouble()).toFloat()
        if (dist < dp(40f)) { scheduleWander(); return }
        v.facingLeft = tx < sx
        v.walking = true
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = (dist / dp(70f) * 1000f).toLong().coerceIn(800L, 9000L) // ~70 dp/s
        anim.interpolator = android.view.animation.LinearInterpolator()
        anim.addUpdateListener { a ->
            val f = a.animatedValue as Float
            lp.x = (sx + (tx - sx) * f).toInt(); lp.y = (sy + (ty - sy) * f).toInt()
            runCatching { wm?.updateViewLayout(v, lp) }
            placeBubble()
        }
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(a: android.animation.Animator) { cancelled = true }
            override fun onAnimationEnd(a: android.animation.Animator) {
                v.walking = false
                if (wanderAnim === anim) wanderAnim = null
                prefs()?.edit()?.putInt("x", lp.x)?.putInt("y", lp.y)?.apply()
                if (!cancelled) scheduleWander()
            }
        })
        wanderAnim = anim
        anim.start()
    }

    private fun ensureAvatar() {
        if (avatar != null) return
        val s = svc ?: return
        val dm = s.resources.displayMetrics
        val safe = safeBounds()
        val asleep = prefs()?.getBoolean("asleep", false) == true
        val sz = size(asleep)
        val v = AvatarView(s).also {
            it.setSleeping(asleep)
            val p = prefs()
            val now = System.currentTimeMillis()
            val m = p?.getInt("mood", 0) ?: 0
            val steps = ((now - (p?.getLong("mood_ts", now) ?: now)) / 21_600_000L).toInt() // humor decai a cada 6 h
            mood = if (m > 0) (m - steps).coerceAtLeast(0) else (m + steps).coerceAtMost(0)
            it.mood = mood
            it.skin = p?.getInt("skin", 5) ?: 5
        }
        val lp = WindowManager.LayoutParams(
            sz, sz, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (prefs()?.getInt("x", safe.right - sz) ?: safe.right - sz)
                .coerceIn(safe.left, (safe.right - sz).coerceAtLeast(safe.left))
            y = (prefs()?.getInt("y", (safe.top + dm.heightPixels * 0.4f).toInt()) ?: (safe.top + dm.heightPixels * 0.4f).toInt())
                .coerceIn(safe.top, (safe.bottom - sz).coerceAtLeast(safe.top))
        }
        v.setOnTouchListener(touch(lp))
        v.contentDescription = if (asleep) "Giselle adormecida. Toque para acordar." else "Avatar Giselle. Toque para falar; mantenha pressionado para dormir."
        v.isFocusable = true
        v.setOnClickListener { onTap() }
        v.setOnLongClickListener {
            setSleeping(avatar?.sleeping != true)
            true
        }
        ViewCompat.addAccessibilityAction(v, "Ativar Giselle") { _, _ -> onTap(); true }
        ViewCompat.addAccessibilityAction(v, "Dormir ou acordar") { _, _ ->
            setSleeping(avatar?.sleeping != true)
            true
        }
        if (runCatching { wm?.addView(v, lp) }.isSuccess) { avatar = v; avatarLp = lp; scheduleWander(4000, 8000) }
    }

    private var mood = 0

    private fun bumpMood(d: Int) {
        mood = (mood + d).coerceIn(-5, 5)
        avatar?.mood = mood
        prefs()?.edit()?.putInt("mood", mood)?.putLong("mood_ts", System.currentTimeMillis())?.apply()
    }

    fun setSkin(name: String) {
        when (name.lowercase().trim()) { // reaproveita SET_SKIN para ligar/desligar o passeio
            "andar", "livre", "passear", "wander" -> { setWander(true); return }
            "parar", "parada", "quieta", "fica" -> { setWander(false); return }
        }
        val idx = when (name.lowercase()) { "neon" -> 1; "noite", "night" -> 2; "padrao" -> 0; "mascote", "mascot", "pixel" -> 4; "orbe", "orb" -> 3; else -> 5 }
        prefs()?.edit()?.putInt("skin", idx)?.apply()
        avatar?.post { avatar?.skin = idx; avatar?.invalidate() }
    }

    private fun setSleeping(b: Boolean) {
        if (b) stopWalk() else scheduleWander(4000, 9000)
        val v = avatar ?: return; val lp = avatarLp ?: return; val s = svc ?: return
        val safe = safeBounds()
        val cx = lp.x + lp.width / 2; val cy = lp.y + lp.height / 2
        v.setSleeping(b)
        lp.width = size(b); lp.height = size(b)
        lp.x = (cx - lp.width / 2).coerceIn(safe.left, (safe.right - lp.width).coerceAtLeast(safe.left))
        lp.y = (cy - lp.height / 2).coerceIn(safe.top, (safe.bottom - lp.height).coerceAtLeast(safe.top))
        runCatching { wm?.updateViewLayout(v, lp) }
        prefs()?.edit()?.putBoolean("asleep", b)?.apply()
        v.contentDescription = if (b) "Giselle adormecida. Toque para acordar."
            else "Avatar Giselle. Toque para falar; mantenha pressionado para dormir."
        removeBubble()
    }

    private fun touch(lp: WindowManager.LayoutParams): View.OnTouchListener {
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false; var longDone = false
        val longPress = Runnable { longDone = true; setSleeping(avatar?.sleeping != true) }
        return View.OnTouchListener { _, e ->
            val d = svc?.resources?.displayMetrics?.density ?: 3f
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    stopWalk(); touching = true
                    dx = e.rawX; dy = e.rawY; sx = lp.x; sy = lp.y; moved = false; longDone = false
                    avatar?.poke()
                    handler.postDelayed(longPress, 800)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(e.rawX - dx) > 10 * d || abs(e.rawY - dy) > 10 * d) { moved = true; handler.removeCallbacks(longPress) }
                    if (moved) {
                        val safe = safeBounds()
                        lp.x = (sx + (e.rawX - dx).toInt()).coerceIn(safe.left, (safe.right - lp.width).coerceAtLeast(safe.left))
                        lp.y = (sy + (e.rawY - dy).toInt()).coerceIn(safe.top, (safe.bottom - lp.height).coerceAtLeast(safe.top))
                        avatar?.lookAt((e.rawX - dx) / (80 * d), (e.rawY - dy) / (80 * d)) // olhos seguem o arrasto
                        avatar?.let { runCatching { wm?.updateViewLayout(it, lp) } }
                        placeBubble()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress); touching = false; scheduleWander(8000, 16000)
                    if (moved) snap(lp) else if (!longDone && e.action == MotionEvent.ACTION_UP) avatar?.performClick()
                }
            }
            true
        }
    }

    private fun onTap() {
        val v = avatar ?: return
        if (v.sleeping) { setSleeping(false); v.react(Face.WINK, 900); bubble("Acordei!", 1800); return }
        val now = System.currentTimeMillis()
        taps.addLast(now); while (taps.isNotEmpty() && now - taps.first() > 1200) taps.removeFirst()
        if (taps.size >= 3) {
            taps.clear()
            v.react(Face.HAPPY, 1800)
            bubble(listOf("Hihi, cócegas!", "Ei, calma!", "Estou aqui 😄").random(), 1800)
        } else if (GiselleBus.state.value == GiselleState.IDLE) {
            WakeWordService.triggerListen()
        }
    }

    /** Encosta à borda mais próxima com um pequeno ressalto e guarda a posição. */
    private fun snap(lp: WindowManager.LayoutParams) {
        if (svc == null) return
        val safe = safeBounds()
        val target = if (lp.x + lp.width / 2 < (safe.left + safe.right) / 2)
            safe.left else safe.right - lp.width
        lp.y = lp.y.coerceIn(safe.top, (safe.bottom - lp.height).coerceAtLeast(safe.top))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) {
            lp.x = target
            avatar?.let { runCatching { wm?.updateViewLayout(it, lp) } }
            prefs()?.edit()?.putInt("x", lp.x)?.putInt("y", lp.y)?.apply()
            return
        }
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(lp.x, target).apply {
            duration = 240; interpolator = OvershootInterpolator(1.4f)
            addUpdateListener {
                lp.x = it.animatedValue as Int
                avatar?.let { v -> runCatching { wm?.updateViewLayout(v, lp) } }
                placeBubble()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    prefs()?.edit()?.putInt("x", lp.x)?.putInt("y", lp.y)?.apply()
                    if (snapAnimator === this@apply) snapAnimator = null
                }
            })
            start()
        }
    }

    // ---------------- balão de fala ----------------
    private fun bubble(text: String, ms: Long) {
        val s = svc ?: return
        val dm = s.resources.displayMetrics
        if (avatar?.sleeping == true && ms < 2000L) return
        bubbleJob?.cancel()
        val tv = bubble ?: TextView(s).apply {
            setTextColor(0xFF10131A.toInt()); textSize = 13f; maxWidth = (dm.widthPixels * 0.6f).toInt()
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            background = GradientDrawable().apply { setColor(Color.argb(240, 255, 255, 255)); cornerRadius = dp(14f).toFloat() }
        }
        val redacted = if (sensitiveSpeech(text)) "Conteúdo oculto por privacidade." else text
        tv.text = redacted.take(260).let { if (redacted.length > 260) "$it…" else it }
        tv.contentDescription = "Fala da Giselle: $redacted"
        val lp = bubbleLp ?: WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        bubbleLp = lp
        place(tv, lp)
        if (bubble == null) { if (runCatching { wm?.addView(tv, lp) }.isSuccess) bubble = tv }
        else runCatching { wm?.updateViewLayout(tv, lp) }
        bubbleJob = scope?.launch { delay(ms); removeBubble() }
    }

    private fun place(tv: TextView, lp: WindowManager.LayoutParams) {
        val dm = svc?.resources?.displayMetrics ?: return
        val safe = safeBounds()
        val a = avatarLp ?: return
        tv.measure(
            View.MeasureSpec.makeMeasureSpec(tv.maxWidth + dp(24f), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val w = tv.measuredWidth; val h = tv.measuredHeight; val m = dp(8f)
        lp.x = (a.x + a.width / 2 - w / 2).coerceIn(safe.left, (safe.right - w).coerceAtLeast(safe.left))
        lp.y = if (a.y - h - m >= safe.top) a.y - h - m else (a.y + a.height + m).coerceAtMost(safe.bottom - h)
    }

    private fun placeBubble() {
        val tv = bubble ?: return; val lp = bubbleLp ?: return
        place(tv, lp); runCatching { wm?.updateViewLayout(tv, lp) }
    }

    private fun removeBubble() {
        bubble?.let { runCatching { wm?.removeView(it) } }
        bubble = null; bubbleLp = null
    }

    private fun sensitiveSpeech(text: String): Boolean {
        val lower = text.lowercase()
        return Regex("""\b(otp|código de verificação|codigo de verificacao|senha|password|pin|cvv|cvc|token)\b""")
            .containsMatchIn(lower) ||
            Regex("""\b\d{6,}\b""").containsMatchIn(text)
    }
}
