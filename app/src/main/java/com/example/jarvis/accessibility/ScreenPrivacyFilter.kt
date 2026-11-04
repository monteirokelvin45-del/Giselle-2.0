package com.example.jarvis.accessibility

import android.graphics.Rect

/** Filtra conteúdo pessoal antes de qualquer texto/imagem da tela sair do aparelho. */
object ScreenPrivacyFilter {
    private val sensitivePackages = setOf(
        "com.whatsapp", "org.telegram.messenger", "com.facebook.orca",
        "com.google.android.gm", "com.microsoft.office.outlook",
        "com.google.android.apps.messaging", "com.android.mms",
        "com.nu.production", "com.itau", "com.bradesco", "com.santander",
        "com.mercadopago", "com.picpay", "com.intermedium", "com.c6bank",
        "com.paypal.android.p2pmobile", "com.revolut.revolut"
    )
    private val sensitivePackageParts = setOf(
        "bank", "banking", "wallet", "whatsapp", "telegram", "messaging", "messages",
        "mail", "email", "nubank", "itau", "bradesco", "santander", "picpay",
        "mercadopago", "intermedium", "c6bank", "paypal", "revolut"
    )
    private val secretLabel = Regex(
        """(?i)\b(password|senha|passcode|pin|otp|one[- ]time|verification code|código|codigo|cvv|cvc|security code|token)\b"""
    )
    private val email = Regex("""\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""", RegexOption.IGNORE_CASE)
    private val longNumber = Regex("""(?<!\d)(?:\d[\s-]?){6,19}(?!\d)""")
    private val standaloneCode = Regex("""^\s*\d{4,8}\s*$""")

    fun isSensitivePackage(packageName: String?): Boolean {
        val pkg = packageName?.lowercase()?.trim().orEmpty()
        if (pkg.isBlank()) return false
        val parts = pkg.split('.').toSet()
        return sensitivePackages.any { pkg == it || pkg.startsWith("$it.") } ||
            parts.any { it in sensitivePackageParts }
    }

    fun redact(value: String): String {
        if (secretLabel.containsMatchIn(value) || standaloneCode.matches(value)) return "[DADO SENSÍVEL OMITIDO]"
        return email.replace(value, "[EMAIL OMITIDO]")
            .let { longNumber.replace(it, "[NÚMERO OMITIDO]") }
    }

    fun shouldMask(value: String?, isPassword: Boolean): Boolean {
        if (isPassword) return true
        val text = value.orEmpty()
        return secretLabel.containsMatchIn(text) || standaloneCode.matches(text) ||
            email.containsMatchIn(text) || longNumber.containsMatchIn(text)
    }

    fun padded(rect: Rect, padding: Int = 8): Rect = Rect(
        rect.left - padding, rect.top - padding, rect.right + padding, rect.bottom + padding
    )
}
