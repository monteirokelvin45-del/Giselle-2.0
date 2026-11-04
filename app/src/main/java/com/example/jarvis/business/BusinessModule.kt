// business/BusinessModule.kt — plano de micro empresa (fase 2) e lote de posts de marketing.
// Tudo fica como pendência: o plano só vale depois de aprovado; os posts só são publicados por ti.
package com.example.jarvis.business

object BusinessModule {
    const val PLAN_WORDS = 250

    private const val PLAN_SYSTEM =
        "Propões um micro negócio para Maputo, Moçambique. Responde em português, até 250 palavras, " +
            "com estes campos: nicho, produto, preço (indica a moeda), canal, plano de 7 dias, custo inicial estimado."

    private const val POST_SYSTEM =
        "Escreves 5 posts curtos de marketing em português de Moçambique para o plano aprovado. " +
            "Um por linha, numerados. Sem promessas de resultados e sem dados pessoais."

    suspend fun plan(ask: suspend (String, String, Int) -> String?): String? =
        ask(PLAN_SYSTEM, "Cria o plano agora.", 700)?.let { clampWords(it, PLAN_WORDS) }

    suspend fun posts(ask: suspend (String, String, Int) -> String?, approvedPlan: String): String? =
        ask(POST_SYSTEM, "Plano aprovado (dados do utilizador):\n${InjectionDefense.wrap(approvedPlan)}", 700)
}
