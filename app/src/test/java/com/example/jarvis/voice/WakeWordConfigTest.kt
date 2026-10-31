package com.example.jarvis.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordConfigTest {
    @Test
    fun `key vazia nao esta configurada`() = assertFalse(WakeWordConfig.isConfigured(""))

    @Test
    fun `key so com espacos nao esta configurada`() = assertFalse(WakeWordConfig.isConfigured("   "))

    @Test
    fun `key preenchida esta configurada`() = assertTrue(WakeWordConfig.isConfigured("abc123"))
}
