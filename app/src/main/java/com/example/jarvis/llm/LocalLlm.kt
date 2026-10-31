package com.example.jarvis.llm

import android.content.Context
import com.example.jarvis.agent.DecisionMaker
import com.example.jarvis.core.AgentDecision
import com.example.jarvis.core.PerceptionContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LLM local (offline) via MediaPipe LLM Inference, carregado por reflexão para o build não
 * depender da biblioteca. Para ativar: descomenta a dependência no build.gradle.kts e põe
 * um modelo .task (ex.: Gemma 2B) em <armazenamento do app>/local-llm.task.
 */
class LocalLlmDecisionMaker(private val context: Context, private val helper: ClaudeClient) : DecisionMaker {

    private val modelFile: File get() = File(context.getExternalFilesDir(null), "local-llm.task")
    private var engine: Any? = null

    private fun ensureEngine(): Any {
        engine?.let { return it }
        val pkg = "com.google.mediapipe.tasks.genai.llminference"
        val inf = Class.forName("$pkg.LlmInference")
        val optsCls = Class.forName("$pkg.LlmInference\$LlmInferenceOptions")
        val bCls = Class.forName("$pkg.LlmInference\$LlmInferenceOptions\$Builder")
        val b = optsCls.getMethod("builder").invoke(null)
        bCls.getMethod("setModelPath", String::class.java).invoke(b, modelFile.absolutePath)
        bCls.getMethod("setMaxTokens", Int::class.javaPrimitiveType).invoke(b, 1024)
        val opts = bCls.getMethod("build").invoke(b)
        val created = inf.getMethod("createFromOptions", Context::class.java, optsCls)
            .invoke(null, context, opts)
            ?: error("MediaPipe não conseguiu criar o mecanismo local")
        engine = created
        return created
    }

    fun available(): Boolean = modelFile.exists() &&
        runCatching { Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference") }.isSuccess

    override suspend fun decide(context: PerceptionContext): DecisionResult = withContext(Dispatchers.Default) {
        if (!modelFile.exists()) return@withContext DecisionResult.Failure("sem modelo local", recoverable = false)
        try {
            val prompt = "<start_of_turn>user\n${ClaudeClient.SYSTEM_PROMPT}\n\n${helper.buildUserPrompt(context)}<end_of_turn>\n<start_of_turn>model\n"
            val e = ensureEngine()
            val out = e.javaClass.getMethod("generateResponse", String::class.java).invoke(e, prompt) as String
            DecisionResult.Success(helper.parseDecision(out))
        } catch (t: Throwable) {
            DecisionResult.Failure("LLM local falhou: ${t.message}", recoverable = false)
        }
    }

    override fun fallbackDecision(reason: String): AgentDecision = helper.fallbackDecision(reason)
}

/** Tenta a nuvem; se falhar (offline/limite), cai para o modelo local. */
class FallbackDecisionMaker(private val primary: DecisionMaker, private val secondary: DecisionMaker) : DecisionMaker {
    override suspend fun decide(context: PerceptionContext): DecisionResult {
        val r = primary.decide(context)
        if (r is DecisionResult.Success) return r
        val s = secondary.decide(context)
        return if (s is DecisionResult.Success) s else r
    }
    override fun fallbackDecision(reason: String): AgentDecision = primary.fallbackDecision(reason)
}

/**
 * Roteamento de modelos: pedidos simples (alarme, volume, abrir app…) vão ao modelo local
 * (se existir) e poupam a quota da nuvem; o resto vai à nuvem, com o local como reserva.
 */
class RoutedDecisionMaker(private val cloud: DecisionMaker, private val local: LocalLlmDecisionMaker) : DecisionMaker {
    private val simple = Regex("alarme|timer|temporizador|volume|abre|abrir|liga |ligar|rotina|gast|nota", RegexOption.IGNORE_CASE)

    override suspend fun decide(context: PerceptionContext): DecisionResult {
        val cmd = context.userCommand.orEmpty()
        val preferLocal = cmd.length < 80 && simple.containsMatchIn(cmd) && local.available()
        val (first, second) = if (preferLocal) local to cloud else cloud to local
        val r = first.decide(context)
        if (r is DecisionResult.Success) return r
        val s = second.decide(context)
        return if (s is DecisionResult.Success) s else r
    }
    override fun fallbackDecision(reason: String): AgentDecision = cloud.fallbackDecision(reason)
}
