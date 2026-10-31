// ============================================================
// tracker/data/AuditedTrackerRepository.kt
// ------------------------------------------------------------
// A regra "toda alteração da IA vira audit_log" (comentário
// original do AuditLog em TrackerModels.kt) só é confiável se
// não depender de cada tela/ViewModel lembrar de chamar duas
// funções em vez de uma. Este repositório é o único caminho
// recomendado para a IA gravar ou alterar dados — ele grava o
// dado E o log de auditoria na mesma chamada, sempre.
//
// Edição manual do usuário (ex: ele corrige um peso digitado
// errado direto na tela) NÃO precisa passar por aqui — o
// comentário original em AuditLog já deixa isso explícito
// ("edição manual não precisa passar por aqui").
// ============================================================

package com.example.jarvis.tracker.data

class AuditedTrackerRepository(
    private val registroFisicoDao: RegistroFisicoDao,
    private val registroFinanceiroDao: RegistroFinanceiroDao,
    private val auditLogDao: TrackerAuditLogDao
) {

    /**
     * Exemplo de escrita "assistida pela IA": insere o registro físico
     * e imediatamente registra a origem no audit_log, na mesma operação
     * lógica — impossível salvar um sem o outro por este caminho.
     */
    suspend fun inserirRegistroFisicoViaIa(registro: RegistroFisico, camposSugeridos: Map<String, String>): Long {
        val id = registroFisicoDao.insert(registro)
        camposSugeridos.forEach { (campo, valor) ->
            auditLogDao.insert(
                AuditLog(
                    tabela = "registro_fisico",
                    registroId = id,
                    campoAlterado = campo,
                    valorAnterior = null, // era um insert novo, não havia valor anterior
                    valorNovo = valor,
                    origem = "ia",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        return id
    }

    /**
     * Alteração de um registro financeiro já existente feita pela IA
     * (ex: reclassificar uma categoria após um insight). Guarda o valor
     * anterior explicitamente — é o que torna a auditoria útil de verdade,
     * não só "sabemos que mudou" mas "sabemos pra o quê e de onde".
     */
    suspend fun atualizarCategoriaFinanceiroViaIa(
        registro: RegistroFinanceiro,
        categoriaAnterior: String
    ) {
        registroFinanceiroDao.update(registro)
        auditLogDao.insert(
            AuditLog(
                tabela = "registro_financeiro",
                registroId = registro.id,
                campoAlterado = "categoria",
                valorAnterior = categoriaAnterior,
                valorNovo = registro.categoria,
                origem = "ia",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    // Padrão a seguir para qualquer nova escrita da IA neste módulo:
    // 1. Fazer o insert/update do dado real
    // 2. Inserir a linha correspondente em audit_log com origem="ia"
    // 3. Nunca expor um DAO de escrita "cru" diretamente para o
    //    orquestrador de IA — só através de um método como estes.
}
