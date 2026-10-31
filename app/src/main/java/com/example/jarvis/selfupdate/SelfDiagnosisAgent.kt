// ============================================================
// selfupdate/SelfDiagnosisAgent.kt
// ------------------------------------------------------------
// Pega erros pendentes (ErrorLogEntity), manda contexto pra
// Claude diagnosticar e sugerir uma correção, e grava a sugestão
// como PatchSuggestion — em texto, formato diff, NUNCA aplicada
// automaticamente ao código-fonte.
//
// LIMITE TÉCNICO REAL, NÃO CONTORNÁVEL:
// um app Android instalado não pode recompilar seu próprio
// Kotlin e se reinstalar sozinho — isso violaria o modelo de
// segurança do Android (toda instalação de APK exige consentimento
// explícito do usuário). Então "correção automática" aqui termina
// em "patch sugerido, pronto pra você revisar e aplicar numa
// próxima build" — nunca em código rodando modificado sozinho.
//
// Isso segue o mesmo espírito do SafetyGate original: a IA nunca
// tem permissão de alterar algo irreversível (aqui, o código-fonte
// que roda em produção) sem confirmação humana explícita.
// ============================================================

package com.example.jarvis.selfupdate

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class PatchStatus { PENDENTE, APROVADO, REJEITADO, APLICADO }

@Entity(tableName = "patch_suggestion")
data class PatchSuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val errorLogId: Long,
    val timestamp: Long,
    val arquivoAlvo: String,          // ex: "core/AppMatcher.kt"
    val diagnostico: String,          // explicação em texto do que causou o erro
    val patchDiff: String,            // diff unificado sugerido
    val confiancaDiagnostico: String, // "alta" | "media" | "baixa" — a IA se autoavalia, mas isso não pula a revisão humana
    val status: String = PatchStatus.PENDENTE.name,
    val revisadoEm: Long? = null
)

@Dao
interface PatchSuggestionDao {
    @Insert
    suspend fun insert(patch: PatchSuggestionEntity): Long

    @Query("SELECT * FROM patch_suggestion WHERE status = :status ORDER BY timestamp DESC")
    fun porStatus(status: String = PatchStatus.PENDENTE.name): Flow<List<PatchSuggestionEntity>>

    @Query("UPDATE patch_suggestion SET status = :status, revisadoEm = :quando WHERE id = :id")
    suspend fun atualizarStatus(id: Long, status: String, quando: Long)
}

/**
 * Contrato mínimo pra chamar o LLM — reaproveita o mesmo ClaudeClient
 * do núcleo, só muda o prompt. Interface pequena aqui só pra manter
 * este arquivo testável sem depender do cliente real.
 */
interface DiagnosisLlmPort {
    suspend fun diagnosticar(prompt: String): String
}

class SelfDiagnosisAgent(
    private val errorLogDao: ErrorLogDao,
    private val patchDao: PatchSuggestionDao,
    private val llm: DiagnosisLlmPort
) {
    /**
     * Roda sob demanda (ex: você abre a tela "Diagnósticos" e aperta
     * "Analisar erros pendentes") ou numa tarefa periódica — nunca
     * dispara sozinha em resposta direta a um crash, pra não gerar
     * chamadas de API descontroladas se o app entrar em crash loop.
     */
    suspend fun diagnosticarPendentes(erros: List<ErrorLogEntity>) {
        for (erro in erros) {
            val prompt = buildPrompt(erro)
            val respostaJson = llm.diagnosticar(prompt)
            val sugestao = parseResposta(erro.id, respostaJson) ?: continue

            patchDao.insert(sugestao)
            errorLogDao.marcarDiagnosticado(erro.id)
        }
    }

    private fun buildPrompt(erro: ErrorLogEntity): String = """
        Você está diagnosticando um erro real de um app Android Kotlin (projeto Jarvis).

        REGRAS OBRIGATÓRIAS:
        - Não invente causa se o stack trace não for suficiente pra ter certeza —
          nesse caso, diga isso explicitamente no diagnóstico e marque confiança "baixa".
        - Sugira o menor patch possível que resolve a causa raiz, não um redesign.
        - Retorne em JSON estrito, sem texto fora do JSON.

        Erro:
        tipo: ${erro.tipo}
        exceção: ${erro.exceptionClass}
        mensagem: ${erro.message}
        stack trace:
        ${erro.stackTrace}
        tela/contexto: ${erro.telaAtual ?: "desconhecido"}
        versão do app: ${erro.versaoApp}

        Responda EXATAMENTE neste formato JSON:
        {
          "arquivo_alvo": "caminho/relativo/Arquivo.kt",
          "diagnostico": "explicação clara da causa raiz",
          "patch_diff": "diff unificado da correção sugerida",
          "confianca": "alta" | "media" | "baixa"
        }
    """.trimIndent()

    private fun parseResposta(errorLogId: Long, json: String): PatchSuggestionEntity? {
        return try {
            val obj = Json.parseToJsonElement(json).jsonObject
            PatchSuggestionEntity(
                errorLogId = errorLogId,
                timestamp = System.currentTimeMillis(),
                arquivoAlvo = obj["arquivo_alvo"]?.jsonPrimitive?.content ?: "desconhecido",
                diagnostico = obj["diagnostico"]?.jsonPrimitive?.content ?: "",
                patchDiff = obj["patch_diff"]?.jsonPrimitive?.content ?: "",
                confiancaDiagnostico = obj["confianca"]?.jsonPrimitive?.content ?: "baixa"
            )
        } catch (_: Exception) {
            // resposta mal formada da IA não vira patch — falha segura,
            // fica só o erro original registrado pra tentar de novo depois
            null
        }
    }
}
