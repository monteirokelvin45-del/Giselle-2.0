// ============================================================
// test/accessibility/NodeTextMatcherTest.kt
// ------------------------------------------------------------
// HONESTIDADE TÉCNICA em vez de teste frágil: `rootInActiveWindow`
// é uma propriedade do AccessibilityService real do Android sem
// shadow estável no Robolectric — não dá pra injetar uma árvore
// de AccessibilityNodeInfo fake ali sem reflection arriscada.
// Testar snapshotScreen()/clickByText() de ponta a ponta exige
// teste INSTRUMENTADO (androidTest, emulador/device real), fora
// do escopo de test/ (JVM puro).
//
// O que dá pra testar sem gambiarra: a lógica de busca de texto
// na árvore foi extraída para NodeTextMatcher, operando sobre
// NodeView (abstração simples) em vez de AccessibilityNodeInfo
// direto — testável sem Android. JarvisAccessibilityService.
// findNodeByText real vira um adapter fino sobre isso.
// ============================================================

package com.example.jarvis.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NodeTextMatcherTest {

    @Test
    fun `encontra no proprio nivel quando o texto bate`() {
        val root = NodeView(text = "Enviar", children = emptyList())
        assertEquals(root, NodeTextMatcher.find(root, "Enviar"))
    }

    @Test
    fun `encontra em nivel profundo da arvore`() {
        val alvo = NodeView(text = "Confirmar")
        val root = NodeView(text = null, children = listOf(
            NodeView(text = "Cancelar"),
            NodeView(text = null, children = listOf(alvo))
        ))
        assertEquals(alvo, NodeTextMatcher.find(root, "Confirmar"))
    }

    @Test
    fun `busca case insensitive com correspondencia exata`() {
        val root = NodeView(text = "Botão ENVIAR agora")
        assertNull(NodeTextMatcher.find(root, "enviar"))
        val exact = NodeView(text = "ENVIAR")
        assertEquals(exact, NodeTextMatcher.find(exact, "enviar"))
    }

    @Test
    fun `ignora no parcial invisivel e desativado`() {
        val exactInvisible = NodeView("Enviar", visible = false)
        val exactDisabled = NodeView("Enviar", enabled = false)
        val exactVisible = NodeView("Enviar", clickable = false)
        val root = NodeView(null, listOf(
            NodeView("Enviar para…"),
            exactInvisible,
            exactDisabled,
            exactVisible
        ))
        assertEquals(exactVisible, NodeTextMatcher.find(root, "Enviar"))
        assertNull(NodeTextMatcher.find(root, "Enviar", requireClickable = true))
    }

    @Test
    fun `retorna null quando nao encontra`() {
        val root = NodeView(text = "Cancelar")
        assertNull(NodeTextMatcher.find(root, "Confirmar"))
    }
}
