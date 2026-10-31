// ============================================================
// accessibility/NodeTextMatcher.kt
// ------------------------------------------------------------
// Lógica pura de busca de texto numa árvore, extraída de
// JarvisAccessibilityService.findNodeByText para ser testável
// sem AccessibilityNodeInfo/Android real.
// ============================================================

package com.example.jarvis.accessibility

/** Visão simplificada e imutável de um nó de tela, independente de Android. */
data class NodeView(
    val text: String?,
    val children: List<NodeView> = emptyList(),
    val visible: Boolean = true,
    val enabled: Boolean = true,
    val clickable: Boolean = true
)

object NodeTextMatcher {
    fun find(node: NodeView, target: String, requireClickable: Boolean = false): NodeView? {
        val wanted = target.trim()
        if (wanted.isEmpty() || !node.visible) return null
        if (node.enabled && (!requireClickable || node.clickable) &&
            node.text?.trim()?.equals(wanted, ignoreCase = true) == true
        ) return node
        for (child in node.children) {
            find(child, target, requireClickable)?.let { return it }
        }
        return null
    }
}
