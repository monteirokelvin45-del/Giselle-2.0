// ============================================================
// tracker/agent/TrackerSafetyGate.kt
// ------------------------------------------------------------
// Equivalente ao SafetyGate do núcleo, mas para o risco
// específico do modo Tracker: gerar um "insight" convincente
// em cima de dados insuficientes. O system prompt do
// TrackerInsightClient já pede "temInsight: false" quando não
// há nada relevante — mas instrução de prompt não é garantia,
// e a mesma regra do núcleo se aplica aqui: a decisão de "isso
// é confiável o suficiente pra mostrar" não pode depender só
// do LLM se autoavaliar.
//
// Roda em dois pontos: ANTES de chamar o LLM (barato, evita
// gastar a chamada quando obviamente não há dado nenhum) e
// DEPOIS (o insight específico que voltou pode ser sobre um
// domínio com pouco dado mesmo que outro domínio tivesse mais).
// ============================================================

package com.example.jarvis.tracker.agent

data class TrackerDataSample(
    val diasComRegistroFisico: Int,
    val diasComRegistroMental: Int,
    val diasComRegistroEstudo: Int,
    val diasComRegistroFinanceiro: Int
)

sealed class TrackerSafetyVerdict {
    object Allowed : TrackerSafetyVerdict()
    data class Blocked(val reason: String) : TrackerSafetyVerdict()
}

object TrackerSafetyGate {

    // Abaixo disso, qualquer "padrão" identificado pelo LLM é mais
    // provável de ser ruído estatístico do que um insight real. Não
    // é um número mágico — é deliberadamente conservador: melhor o
    // usuário sentir "ainda não gerou nada" por mais alguns dias do
    // que receber um diagnóstico prematuro e perder confiança no
    // recurso inteiro por causa de um exemplo com 1-2 pontos de dado.
    const val MINIMO_DIAS_POR_DOMINIO = 3

    /**
     * Checagem PRÉ-chamada: se nenhum domínio tem dado suficiente,
     * nem vale gastar uma chamada de API — o resultado mais honesto
     * já é "sem dados o bastante", sem precisar perguntar ao LLM.
     */
    fun evaluateBeforeCall(sample: TrackerDataSample): TrackerSafetyVerdict {
        val algumDominioComDadoSuficiente = listOf(
            sample.diasComRegistroFisico,
            sample.diasComRegistroMental,
            sample.diasComRegistroEstudo,
            sample.diasComRegistroFinanceiro
        ).any { it >= MINIMO_DIAS_POR_DOMINIO }

        return if (algumDominioComDadoSuficiente) {
            TrackerSafetyVerdict.Allowed
        } else {
            TrackerSafetyVerdict.Blocked(
                "Ainda não há dias suficientes registrados em nenhuma área " +
                    "(mínimo de $MINIMO_DIAS_POR_DOMINIO) para gerar um diagnóstico confiável."
            )
        }
    }

    /**
     * Checagem PÓS-resposta: mesmo que a pré-checagem tenha passado
     * (porque outro domínio tinha dado suficiente), o insight
     * específico que voltou pode ser sobre um domínio que NÃO tem
     * amostra suficiente — ex: pediu diagnóstico geral, mas o LLM
     * decidiu falar especificamente de finanças com só 1 registro.
     */
    fun evaluateInsight(insight: TrackerInsight, sample: TrackerDataSample): TrackerSafetyVerdict {
        val diasDoDominio = when (insight.tipo) {
            TrackerInsightType.INSIGHT_PESO, TrackerInsightType.INSIGHT_TREINO ->
                sample.diasComRegistroFisico
            TrackerInsightType.INSIGHT_ESTUDO ->
                sample.diasComRegistroEstudo
            TrackerInsightType.INSIGHT_FINANCAS ->
                sample.diasComRegistroFinanceiro
            TrackerInsightType.INSIGHT_CONSISTENCIA ->
                sample.diasComRegistroMental
            TrackerInsightType.DIAGNOSTICO_GERAL -> null // não é bloqueado por domínio único
            TrackerInsightType.DESCONHECIDO ->
                return TrackerSafetyVerdict.Blocked(
                    "Tipo de insight não reconhecido (\"${insight.tipo}\") — provável erro de geração do modelo. Descartado por segurança."
                )
        }

        if (diasDoDominio != null && diasDoDominio < MINIMO_DIAS_POR_DOMINIO) {
            return TrackerSafetyVerdict.Blocked(
                "O insight gerado (${insight.tipo}) se baseia em menos de " +
                    "$MINIMO_DIAS_POR_DOMINIO dias de dados nesse domínio — descartado por segurança."
            )
        }

        return TrackerSafetyVerdict.Allowed
    }
}
