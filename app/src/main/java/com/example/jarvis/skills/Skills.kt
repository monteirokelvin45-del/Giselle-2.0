package com.example.jarvis.skills

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.example.jarvis.agent.AppLauncherPort
import com.example.jarvis.agent.ScreenActuator
import com.example.jarvis.core.GiselleBus
import com.example.jarvis.vision.VisionPort
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Receita em dados (JSON): passos open/click/type/wait/back/home. `${input}` é substituído pelo texto do comando. */
data class SkillStep(val op: String, val target: String? = null, val text: String? = null, val ms: Long = 0)
data class Skill(val name: String, val triggers: List<String>, val steps: List<SkillStep>)

object SkillStore {
    @Volatile var catalog: String = ""

    fun norm(n: String) = n.lowercase().trim().replace(Regex("[^a-z0-9à-ú]+"), "_").trim('_')
    private fun dir(ctx: Context) = File(ctx.filesDir, "skills").apply { mkdirs() }

    fun save(ctx: Context, s: Skill) {
        val j = JSONObject().put("name", s.name).put("triggers", JSONArray(s.triggers)).put("steps", JSONArray().apply {
            s.steps.forEach { put(JSONObject().put("op", it.op).put("target", it.target).put("text", it.text).put("ms", it.ms)) }
        })
        File(dir(ctx), "${s.name}.json").writeText(j.toString(2))
        refresh(ctx)
    }

    fun all(ctx: Context): List<Skill> = dir(ctx).listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull {
        runCatching {
            val j = JSONObject(it.readText())
            Skill(
                j.getString("name"),
                j.optJSONArray("triggers")?.let { a -> (0 until a.length()).map { i -> a.getString(i) } } ?: emptyList(),
                j.getJSONArray("steps").let { a ->
                    (0 until a.length()).map { i ->
                        a.getJSONObject(i).let { o ->
                            SkillStep(o.getString("op"), o.optString("target").takeIf { t -> t.isNotBlank() && t != "null" },
                                o.optString("text").takeIf { t -> t.isNotBlank() && t != "null" }, o.optLong("ms"))
                        }
                    }
                }
            )
        }.getOrNull()
    }

    fun get(ctx: Context, name: String): Skill? = all(ctx).firstOrNull { it.name == norm(name) }

    /** Atalho sem LLM: o comando contém um gatilho (ou o nome) de uma skill. */
    fun match(ctx: Context, command: String): Skill? {
        val c = command.lowercase()
        return all(ctx).firstOrNull { s -> (s.triggers + s.name.replace('_', ' ')).any { it.isNotBlank() && c.contains(it.lowercase()) } }
    }

    fun refresh(ctx: Context) { catalog = all(ctx).joinToString(", ") { it.name } }
}

class SkillRunner(
    private val launcher: AppLauncherPort,
    private val screen: ScreenActuator,
    private val vision: VisionPort?
) {
    suspend fun run(skill: Skill, input: String): Boolean {
        for (st in skill.steps) {
            val target = st.target?.replace("\${input}", input)
            val text = st.text?.replace("\${input}", input)
            val ok = when (st.op) {
                "open" -> target != null && launcher.openByName(target).also { delay(2000) }
                "click" -> target != null && (screen.click(target) ||
                    (vision?.locate(target)?.let { screen.tapAt(it.x, it.y) } ?: false)).also { delay(600) }
                "type" -> text != null && screen.typeText(text)
                "wait" -> { delay(st.ms.coerceIn(0, 5000)); true }
                "back" -> screen.goBack().also { delay(400) }
                "home" -> screen.goHome().also { delay(400) }
                else -> true
            }
            if (!ok) return false
        }
        return true
    }
}

/** Grava o que o utilizador faz (cliques e texto) e guarda como skill. Campos de palavra-passe são ignorados. */
object SkillRecorder {
    @Volatile var active: String? = null
        private set
    private val steps = ArrayList<SkillStep>()
    private var started = false
    private var lastTs = 0L

    fun start(name: String): Boolean {
        val n = SkillStore.norm(name)
        if (n.isBlank()) return false
        synchronized(steps) { steps.clear(); started = false; lastTs = 0 }
        active = n
        GiselleBus.say("A aprender \"$n\"… faz a tarefa e diz \"terminei\".")
        return true
    }

    fun stop(ctx: Context): Boolean {
        val n = active ?: return false
        active = null
        val copy = synchronized(steps) { steps.toList() }
        if (copy.isEmpty()) { GiselleBus.say("Não vi nenhum passo."); return false }
        SkillStore.save(ctx, Skill(n, listOf(n.replace('_', ' ')), copy))
        GiselleBus.say("Aprendi \"$n\" com ${copy.size} passos.")
        return true
    }

    fun onEvent(svc: AccessibilityService, e: AccessibilityEvent) {
        if (active == null) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == svc.packageName || pkg == "com.android.systemui") return
        val now = System.currentTimeMillis()
        synchronized(steps) {
            if (!started) {
                started = true
                val label = runCatching {
                    svc.packageManager.getApplicationLabel(svc.packageManager.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                steps += SkillStep("open", target = label); lastTs = now
            }
            when (e.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    val t = labelOf(e) ?: return
                    if (lastTs > 0 && now - lastTs > 1200) steps += SkillStep("wait", ms = (now - lastTs).coerceAtMost(3000))
                    steps += SkillStep("click", target = t); lastTs = now
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    if (e.isPassword) return
                    val txt = e.text?.joinToString("") ?: return
                    if (steps.lastOrNull()?.op == "type") steps[steps.lastIndex] = SkillStep("type", text = txt)
                    else steps += SkillStep("type", text = txt)
                    lastTs = now
                }
            }
        }
    }

    private fun labelOf(e: AccessibilityEvent): String? =
        e.text?.firstOrNull()?.toString()?.takeIf { it.isNotBlank() && it.length < 60 }
            ?: e.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            ?: e.source?.let { it.text?.toString() ?: it.contentDescription?.toString() ?: it.viewIdResourceName?.substringAfter(":id/") }
}
