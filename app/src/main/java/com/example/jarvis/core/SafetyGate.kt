// ============================================================
// core/SafetyGate.kt
// ------------------------------------------------------------
// PRINCÍPIO DE DESIGN (não negociável): o LLM decide O QUE fazer,
// mas NUNCA decide se precisa de confirmação. Essa decisão é
// hard-coded aqui, fora do alcance de prompt injection ou
// alucinação do modelo. Isso é o que separa um assistente
// confiável de um risco de segurança.
// ============================================================

package com.example.jarvis.core

object SafetyGate {

    // Ações que são SEMPRE irreversíveis, não importa o app
    private val hardBlockedActions = setOf(
        "deletar", "excluir", "apagar", "delete", "remove account", "remover conta",
        "formatar", "reset de fábrica", "factory reset"
    )

    // Apps que envolvem dinheiro ou terceiros = risco elevado por padrão
    private val sensitiveAppPatterns = listOf(
        "nubank", "itau", "bradesco", "santander", "picpay", "mercadopago",
        "whatsapp", "telegram", "instagram", "gmail", "outlook",
        "ifood", "rappi", "uber", "99", "amazon", "mercadolivre", "shopee",
        "paypal", "wise", "venmo", "cashapp", "revolut", "bank",
        // Moçambique: dinheiro móvel e bancos
        "mpesa", "emola", "mkesh", "millennium", "standardbank", "absa", "ecobank"
    )

    // Palavras que indicam ação de envio/compra/publicação (irreversível ou pública)
    private val irreversibleIntentPatterns = listOf(
        "enviar", "mandar", "comprar", "pagar", "transferir", "publicar",
        "postar", "confirmar pedido", "finalizar compra", "assinar",
        "send", "pay", "buy", "purchase", "transfer", "publish", "post",
        "confirm", "checkout", "subscribe", "submit", "place order"
    )

    fun evaluate(decision: AgentDecision, currentApp: String): RiskLevel {
        val target = normalize(decision.target.orEmpty())
        val summary = normalize(decision.userFacingSummary)
        val appSegments = currentApp.lowercase().split(Regex("[^a-z0-9]+")).filter(String::isNotBlank)

        // 1. Bloqueio duro: nunca passa sem confirmação, ponto final
        if (hardBlockedActions.any { containsPhrase(target, it) || containsPhrase(summary, it) }) {
            return RiskLevel.IRREVERSIBLE
        }

        // 2. App sensível + intenção de ação irreversível = confirma
        val isAppSensitive = sensitiveAppPatterns.any { pattern ->
            val p = normalize(pattern)
            if (p.all(Char::isDigit)) appSegments.any { it == p }
            else appSegments.any { it == p } || containsPhrase(currentApp.lowercase(), p)
        }
        val hasIrreversibleIntent = irreversibleIntentPatterns.any {
            containsPhrase(target, normalize(it)) || containsPhrase(summary, normalize(it))
        }
        if (isAppSensitive && hasIrreversibleIntent) {
            return RiskLevel.SENSITIVE
        }

        // 3. Digitar texto em campos de senha/dados sensíveis
        if (decision.actionType == ActionType.CALL || decision.actionType == ActionType.SEND_SMS ||
            decision.actionType == ActionType.APP_MESSAGE ||
            decision.actionType == ActionType.REPLY_NOTIFICATION || decision.actionType == ActionType.ANSWER_CALL ||
            decision.actionType == ActionType.REJECT_CALL || decision.actionType == ActionType.OPEN_URL ||
            decision.actionType == ActionType.REMEMBER || decision.actionType == ActionType.SET_ROUTINE ||
            decision.actionType == ActionType.RUN_SKILL || decision.actionType == ActionType.LEARN_SKILL ||
            decision.actionType == ActionType.LOG_EXPENSE ||
            decision.actionType == ActionType.MOBILE_MONEY ||
            decision.actionType == ActionType.TYPE_TEXT && hasIrreversibleIntent ||
            decision.actionType == ActionType.CLICK && hasIrreversibleIntent ||
            decision.actionType == ActionType.SWIPE && hasIrreversibleIntent
        ) {
            return RiskLevel.SENSITIVE
        }

        if (decision.actionType == ActionType.TYPE_TEXT &&
            listOf("senha", "password", "passcode", "pin", "cvv", "cvc", "otp", "one time", "verification code",
                "codigo", "código", "code", "cpf", "card number", "cartao", "cartão", "security code")
                .any { containsPhrase(target, normalize(it)) || containsPhrase(summary, normalize(it)) }
        ) {
            return RiskLevel.SENSITIVE
        }

        // 4. Tudo mais (navegar, ler, organizar, abrir apps) = seguro, autonomia total
        return RiskLevel.SAFE
    }

    /**
     * Aplica a avaliação sobre a decisão do LLM, sobrescrevendo
     * requiresConfirmation independente do que o modelo tenha sugerido.
     */
    fun apply(decision: AgentDecision, currentApp: String): AgentDecision {
        val risk = evaluate(decision, currentApp)
        val requiresConfirmation = risk != RiskLevel.SAFE
        return decision.copy(requiresConfirmation = requiresConfirmation)
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun containsPhrase(text: String, phrase: String): Boolean =
        phrase.isNotBlank() && (" $text ").contains(" $phrase ")
}
