package com.example.jarvis.selfupdate

import org.junit.Assert.*
import org.junit.Test

class PatchSafetyGateTest {

    private fun patch(arquivo: String, confianca: String) = PatchSuggestionEntity(
        errorLogId = 1,
        timestamp = 0,
        arquivoAlvo = arquivo,
        diagnostico = "teste",
        patchDiff = "",
        confiancaDiagnostico = confianca
    )

    @Test
    fun `qualquer patch sempre requer aprovacao humana`() {
        val alta = patch("core/AppMatcher.kt", "alta")
        val baixa = patch("core/AppMatcher.kt", "baixa")
        assertTrue(PatchSafetyGate.requerAprovacaoHumana(alta))
        assertTrue(PatchSafetyGate.requerAprovacaoHumana(baixa))
    }

    @Test
    fun `patch em arquivo critico e rebaixado para baixa confianca mesmo se IA disse alta`() {
        val patch = patch("core/SafetyGate.kt", "alta")
        assertEquals("baixa", PatchSafetyGate.confiancaEfetiva(patch))
    }

    @Test
    fun `patch em arquivo comum mantem a confianca original da IA`() {
        val patch = patch("tracker/ui/DashboardScreen.kt", "alta")
        assertEquals("alta", PatchSafetyGate.confiancaEfetiva(patch))
    }

    @Test
    fun `deteccao de arquivo critico funciona com caminho completo`() {
        val patch = patch("app/src/main/java/com/example/jarvis/tracker/agent/TrackerSafetyGate.kt", "alta")
        assertEquals("baixa", PatchSafetyGate.confiancaEfetiva(patch))
    }
}
