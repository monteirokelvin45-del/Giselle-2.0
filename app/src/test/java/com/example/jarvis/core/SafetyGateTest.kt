// ============================================================
// test/core/SafetyGateTest.kt
// ------------------------------------------------------------
// SafetyGate é o componente que decide autonomia vs confirmação.
// É o componente com MAIOR custo de erro no projeto inteiro —
// por isso tem a cobertura de teste mais explícita. Um teste
// que falha aqui deve travar qualquer CI antes de mergear.
// ============================================================

package com.example.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyGateTest {

    private fun decision(
        target: String? = null,
        summary: String = "",
        actionType: ActionType = ActionType.CLICK
    ) = AgentDecision(
        reasoning = "teste",
        actionType = actionType,
        target = target,
        requiresConfirmation = false, // sempre false na entrada — é o que o SafetyGate deve corrigir
        userFacingSummary = summary
    )

    // -----------------------------------------------------
    // Ações seguras (navegação, leitura) — autonomia total
    // -----------------------------------------------------
    @Test
    fun `clicar em botao generico em app nao sensivel e seguro`() {
        val d = decision(target = "Configurações", summary = "Vou abrir as configurações")
        assertEquals(RiskLevel.SAFE, SafetyGate.evaluate(d, "com.android.settings"))
    }

    @Test
    fun `navegar de volta e sempre seguro mesmo em app sensivel`() {
        val d = decision(actionType = ActionType.GO_BACK, summary = "Voltando")
        assertEquals(RiskLevel.SAFE, SafetyGate.evaluate(d, "com.whatsapp"))
    }

    @Test
    fun `abrir app e seguro por padrao`() {
        val d = decision(actionType = ActionType.OPEN_APP, target = "Spotify", summary = "Abrindo Spotify")
        assertEquals(RiskLevel.SAFE, SafetyGate.evaluate(d, "com.android.launcher"))
    }

    // -----------------------------------------------------
    // Bloqueio duro — nunca passa sem confirmação, nenhuma exceção
    // -----------------------------------------------------
    @Test
    fun `deletar e sempre irreversivel independente do app`() {
        val d = decision(target = "Deletar conversa", summary = "Vou deletar a conversa")
        assertEquals(RiskLevel.IRREVERSIBLE, SafetyGate.evaluate(d, "com.whatsapp"))
    }

    @Test
    fun `excluir e bloqueado mesmo em app nao sensivel`() {
        val d = decision(target = "Excluir arquivo", summary = "Excluindo o arquivo")
        assertEquals(RiskLevel.IRREVERSIBLE, SafetyGate.evaluate(d, "com.android.documentsui"))
    }

    @Test
    fun `reset de fabrica e sempre bloqueado`() {
        val d = decision(summary = "Vou fazer reset de fábrica no aparelho")
        assertEquals(RiskLevel.IRREVERSIBLE, SafetyGate.evaluate(d, "com.android.settings"))
    }

    // -----------------------------------------------------
    // App sensível + intenção irreversível = confirma
    // -----------------------------------------------------
    @Test
    fun `enviar mensagem no whatsapp exige confirmacao`() {
        val d = decision(target = "Enviar", summary = "Vou enviar a mensagem")
        assertEquals(RiskLevel.SENSITIVE, SafetyGate.evaluate(d, "com.whatsapp"))
    }

    @Test
    fun `finalizar compra no ifood exige confirmacao`() {
        val d = decision(target = "Finalizar pedido", summary = "Vou finalizar a compra no iFood")
        assertEquals(RiskLevel.SENSITIVE, SafetyGate.evaluate(d, "com.ifood.app"))
    }

    @Test
    fun `english send pay buy confirm exigem confirmacao em app sensivel`() {
        listOf("send payment", "pay now", "buy item", "confirm order").forEach { phrase ->
            val d = decision(target = phrase, summary = phrase)
            assertEquals(RiskLevel.SENSITIVE, SafetyGate.evaluate(d, "com.paypal.android"))
        }
    }

    @Test
    fun `identificador 99 exige limite por segmento e nao substring`() {
        val sensitive = decision(target = "pay", summary = "pay")
        val neutral = decision(target = "open", summary = "open")
        assertEquals(RiskLevel.SENSITIVE, SafetyGate.evaluate(sensitive, "com.app.99"))
        assertEquals(RiskLevel.SAFE, SafetyGate.evaluate(neutral, "com.app.199"))
    }

    @Test
    fun `acoes persistentes e url sempre pedem confirmacao`() {
        listOf(ActionType.OPEN_URL, ActionType.REMEMBER, ActionType.SET_ROUTINE,
            ActionType.RUN_SKILL, ActionType.LEARN_SKILL).forEach { action ->
            assertEquals(RiskLevel.SENSITIVE, SafetyGate.evaluate(decision(actionType = action), "com.example.app"))
        }
    }

    @Test
    fun `transferir dinheiro no banco exige confirmacao`() {
        val d = decision(target = "Transferir", summary = "Vou transferir o valor")
        assertEquals(RiskLevel.SENSITIVE, SafetyGate.evaluate(d, "com.nubank.app"))
    }

    @Test
    fun `navegar dentro do banco sem intencao de transferir e seguro`() {
        val d = decision(target = "Extrato", summary = "Vou abrir o extrato")
        assertEquals(RiskLevel.SAFE, SafetyGate.evaluate(d, "com.nubank.app"))
    }

    @Test
    fun `enviar em app nao sensivel nao exige confirmacao`() {
        // "enviar" sozinho não é gatilho — só combinado com app sensível
        val d = decision(target = "Enviar formulário", summary = "Vou enviar o formulário")
        assertEquals(RiskLevel.SAFE, SafetyGate.evaluate(d, "com.example.formapp"))
    }

    // -----------------------------------------------------
    // Campos sensíveis (senha, CVV, CPF)
    // -----------------------------------------------------
    @Test
    fun `digitar em campo de senha exige confirmacao`() {
        val d = decision(
            target = "Senha", summary = "Digitando a senha",
            actionType = ActionType.TYPE_TEXT
        )
        assertEquals(RiskLevel.SENSITIVE, SafetyGate.evaluate(d, "com.example.anyapp"))
    }

    @Test
    fun `digitar cvv exige confirmacao mesmo fora de app financeiro`() {
        val d = decision(
            target = "CVV", summary = "Digitando o CVV",
            actionType = ActionType.TYPE_TEXT
        )
        assertEquals(RiskLevel.SENSITIVE, SafetyGate.evaluate(d, "com.example.anyapp"))
    }

    // -----------------------------------------------------
    // apply() deve sobrescrever requiresConfirmation vindo do LLM,
    // independente do que o modelo tenha decidido
    // -----------------------------------------------------
    @Test
    fun `apply sobrescreve requiresConfirmation mesmo se LLM disse false`() {
        val d = decision(target = "Deletar tudo", summary = "Deletando").copy(requiresConfirmation = false)
        val result = SafetyGate.apply(d, "com.example.anyapp")
        assertEquals(true, result.requiresConfirmation)
    }

    @Test
    fun `apply mantem requiresConfirmation false para acoes seguras`() {
        val d = decision(target = "Abrir menu", summary = "Abrindo o menu")
        val result = SafetyGate.apply(d, "com.example.anyapp")
        assertEquals(false, result.requiresConfirmation)
    }

    @Test
    fun `apply nao pode ser contornado mesmo se LLM tentar forcar true incorretamente`() {
        // Mesmo que o LLM alucine requiresConfirmation=true numa ação
        // trivial, isso não é um risco de segurança (é excesso de cautela,
        // não falta dela) — mas o teste documenta que o resultado final
        // é sempre ditado pelo SafetyGate, nunca pelo campo vindo do LLM.
        val d = decision(target = "Abrir menu", summary = "Abrindo o menu").copy(requiresConfirmation = true)
        val result = SafetyGate.apply(d, "com.example.anyapp")
        assertEquals(false, result.requiresConfirmation)
    }
}
