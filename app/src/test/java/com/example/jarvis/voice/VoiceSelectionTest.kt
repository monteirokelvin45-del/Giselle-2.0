package com.example.jarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceSelectionTest {

    private fun v(nome: String, pais: String = "BR", qualidade: Int = 300, rede: Boolean = false) =
        VoiceCandidate(name = nome, language = "pt", country = pais, quality = qualidade, networkRequired = rede)

    @Test
    fun `sem voz portuguesa devolve null`() {
        val en = VoiceCandidate("en-us-x", "en", "US", 300, false)
        assertNull(VoiceSelection.escolher(listOf(en)))
    }

    @Test
    fun `prefere pt-BR a pt-PT`() {
        val escolhida = VoiceSelection.escolher(listOf(v("pt-pt-x", pais = "PT"), v("pt-br-x", pais = "BR")))
        assertEquals("pt-br-x", escolhida?.name)
    }

    @Test
    fun `prefere voz feminina pelo nome`() {
        val escolhida = VoiceSelection.escolher(listOf(v("pt-br-male-1"), v("pt-br-female-1")))
        assertEquals("pt-br-female-1", escolhida?.name)
    }

    @Test
    fun `preferencia explicita do utilizador vence a heuristica`() {
        val escolhida = VoiceSelection.escolher(listOf(v("pt-br-female-1"), v("pt-br-male-1")), preferida = "pt-br-male-1")
        assertEquals("pt-br-male-1", escolhida?.name)
    }

    @Test
    fun `qualidade mais alta ganha entre vozes do mesmo pais`() {
        val escolhida = VoiceSelection.escolher(listOf(v("a", qualidade = 200), v("b", qualidade = 400)))
        assertEquals("b", escolhida?.name)
    }

    @Test
    fun `mensagens de erro distinguem rede, silencio e permissao`() {
        assertEquals("Sem conexão para reconhecer a voz.", VoiceSelection.mensagemErroReconhecimento(2))
        assertEquals("Não ouvi nada útil. Tente de novo.", VoiceSelection.mensagemErroReconhecimento(7))
        assertEquals(true, VoiceSelection.mensagemErroReconhecimento(9).startsWith("Permissão"))
    }
}
