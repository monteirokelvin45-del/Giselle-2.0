package com.example.jarvis.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenPrivacyFilterTest {
    @Test
    fun `bloqueia apps de conversa e banco`() {
        assertTrue(ScreenPrivacyFilter.isSensitivePackage("com.whatsapp"))
        assertTrue(ScreenPrivacyFilter.isSensitivePackage("com.example.bank"))
        assertFalse(ScreenPrivacyFilter.isSensitivePackage("com.spotify.music"))
    }

    @Test
    fun `redige segredos codigos email e numeros longos`() {
        val text = ScreenPrivacyFilter.redact("Password: hunter2, código 123456, a@b.com")
        assertFalse(text.contains("hunter2"))
        assertFalse(text.contains("123456"))
        assertFalse(text.contains("a@b.com"))
        assertEquals("[DADO SENSÍVEL OMITIDO]", text)
    }
}
