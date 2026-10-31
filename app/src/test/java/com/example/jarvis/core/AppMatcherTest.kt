// ============================================================
// test/core/AppMatcherTest.kt
// ------------------------------------------------------------
// Cobre os 3 níveis de matching e a desambiguação: nome genérico
// que bate com múltiplos apps vira Ambiguous, nunca escolhe o
// primeiro da lista em silêncio.
// ============================================================

package com.example.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMatcherTest {

    private val sampleApps = listOf(
        LaunchableApp(packageName = "com.whatsapp", label = "WhatsApp"),
        LaunchableApp(packageName = "com.google.android.gm", label = "Gmail"),
        LaunchableApp(packageName = "com.spotify.music", label = "Spotify"),
        LaunchableApp(packageName = "com.android.settings", label = "Configurações"),
        LaunchableApp(packageName = "com.instagram.android", label = "Instagram")
    )

    @Test
    fun `match exato de label encontra o app certo`() {
        val result = AppMatcher.resolve("whatsapp", sampleApps)
        assertTrue(result is AppMatchResult.Exact)
        assertEquals("com.whatsapp", (result as AppMatchResult.Exact).app.packageName)
    }

    @Test
    fun `match parcial unico ainda e Exact`() {
        val result = AppMatcher.resolve("whats", sampleApps)
        assertTrue(result is AppMatchResult.Exact)
        assertEquals("com.whatsapp", (result as AppMatchResult.Exact).app.packageName)
    }

    @Test
    fun `nome generico que bate com varios apps vira Ambiguous`() {
        val apps = listOf(
            LaunchableApp(packageName = "com.google.android.apps.messaging", label = "Mensagens"),
            LaunchableApp(packageName = "com.samsung.android.messaging", label = "Mensagens Samsung")
        )
        val result = AppMatcher.resolve("mensagens", apps)
        assertTrue(result is AppMatchResult.Ambiguous)
        assertEquals(2, (result as AppMatchResult.Ambiguous).candidates.size)
    }

    @Test
    fun `match so por substring de pacote e WeakMatch, nunca abre direto`() {
        val apps = listOf(LaunchableApp(packageName = "com.spotify.music", label = "Música"))
        val result = AppMatcher.resolve("spotify", apps)
        assertTrue(result is AppMatchResult.WeakMatch)
    }

    @Test
    fun `nome sem nenhum match retorna NoMatch`() {
        assertEquals(AppMatchResult.NoMatch, AppMatcher.resolve("aplicativoquenaoexiste", sampleApps))
    }

    @Test
    fun `nome em branco retorna NoMatch`() {
        assertEquals(AppMatchResult.NoMatch, AppMatcher.resolve("   ", sampleApps))
    }

    @Test
    fun `lista vazia retorna NoMatch`() {
        assertEquals(AppMatchResult.NoMatch, AppMatcher.resolve("whatsapp", emptyList()))
    }

    @Test
    fun `prioriza match exato sobre parcial quando ambos existem`() {
        val apps = listOf(
            LaunchableApp(packageName = "com.exact", label = "Notas"),
            LaunchableApp(packageName = "com.partial", label = "Notas Rápidas")
        )
        val result = AppMatcher.resolve("notas", apps)
        assertTrue(result is AppMatchResult.Exact)
        assertEquals("com.exact", (result as AppMatchResult.Exact).app.packageName)
    }

    @Test
    fun `normalize remove acentos e caracteres especiais`() {
        assertEquals("configuracoes", AppMatcher.normalize("Configurações"))
        assertEquals("whatsapp", AppMatcher.normalize("  WhatsApp!! "))
    }
}
