// ============================================================
// JarvisAccessibilityService.kt
// Núcleo de percepção + ação do "cérebro" no Android.
// Lê a tela em tempo real e executa ações (toques, swipes, texto).
// ============================================================

package com.example.jarvis.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "JarvisA11y"
        // Referência estática pra outros componentes (Agent, ViewModel) chamarem ações
        @Volatile
        var instance: JarvisAccessibilityService? = null
    }

    @Volatile
    private var lastKnownPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        com.example.jarvis.visual.OverlayController.attach(this)
        Log.d(TAG, "Serviço conectado — olhos abertos.")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        releaseServiceReference()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        releaseServiceReference()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.example.jarvis.visual.OverlayController.onConfigurationChanged()
    }

    private fun releaseServiceReference() {
        com.example.jarvis.visual.OverlayController.detach(this)
        if (instance === this) instance = null
    }

    // -----------------------------------------------------
    // PERCEPÇÃO: chamado toda vez que algo muda na tela
    // -----------------------------------------------------
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        ) com.example.jarvis.skills.SkillRecorder.onEvent(this, event)

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: "desconhecido"
                lastKnownPackage = pkg
                Log.d(TAG, "App em foco mudou: $pkg")
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                // Log de interações do usuário -> alimenta a memória de aprendizado
                // AgentOrchestrator.logUserAction(event)
            }
            else -> {}
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Serviço interrompido.")
    }

    // -----------------------------------------------------
    // PERCEPÇÃO ATIVA: tira um "snapshot" da árvore de tela atual
    // Retorna texto/hierarquia legível pro LLM decidir o que fazer
    // -----------------------------------------------------
    fun snapshotScreen(): String {
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return "Sem acesso à tela atual."
        val builder = StringBuilder()
        try {
            val sensitive = ScreenPrivacyFilter.isSensitivePackage(root.packageName?.toString() ?: lastKnownPackage)
            traverseNode(root, builder, depth = 0, redactAll = sensitive)
        } finally {
            root.recycle()
        }
        return builder.toString()
    }

    fun sensitiveScreenBounds(): List<Rect> {
        val root = rootInActiveWindow ?: return emptyList()
        val result = ArrayList<Rect>()
        fun visit(node: AccessibilityNodeInfo) {
            if (node.isVisibleToUser) {
                val value = "${node.text ?: ""} ${node.contentDescription ?: ""}"
                if (ScreenPrivacyFilter.shouldMask(value, node.isPassword)) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (!rect.isEmpty) result += ScreenPrivacyFilter.padded(rect)
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { child ->
                visit(child)
                child.recycle()
            }
        }
        try {
            visit(root)
        } finally {
            root.recycle()
        }
        return result
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        builder: StringBuilder,
        depth: Int,
        redactAll: Boolean
    ) {
        val indent = "  ".repeat(depth)
        val rawText = node.text?.toString().orEmpty()
        val rawDesc = node.contentDescription?.toString().orEmpty()
        val text = if (redactAll) "[conteúdo de app sensível omitido]"
            else if (node.isPassword) "[senha omitida]" else ScreenPrivacyFilter.redact(rawText)
        val desc = if (redactAll) "[conteúdo omitido]" else ScreenPrivacyFilter.redact(rawDesc)
        val cls = node.className?.toString().orEmpty()

        if (text.isNotBlank() || desc.isNotBlank()) {
            builder.append("$indent[$cls] text='$text' desc='$desc' clickable=${node.isClickable}\n")
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseNode(child, builder, depth + 1, redactAll)
                child.recycle()
            }
        }
    }

    // -----------------------------------------------------
    // AÇÃO: clicar em um nó pelo texto (ex: "Enviar", "Confirmar")
    // -----------------------------------------------------
    fun clickByText(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            val node = findNodeByText(root, target, requireClickable = true) ?: return false
            return try {
                performClick(node)
            } finally {
                node.recycle()
            }
        } finally {
            root.recycle()
        }
    }

    // Mesma regra de match do NodeTextMatcher (testado em NodeTextMatcherTest) —
    // mantido inline aqui em vez de delegar porque converter a árvore real pra
    // NodeView a cada clique teria custo de runtime sem ganho real.
    private fun findNodeByText(
        node: AccessibilityNodeInfo,
        target: String,
        requireClickable: Boolean = false
    ): AccessibilityNodeInfo? {
        val wanted = target.trim()
        if (wanted.isEmpty()) return null
        val matches = ArrayList<AccessibilityNodeInfo>()
        fun visit(current: AccessibilityNodeInfo) {
            if (!current.isVisibleToUser || !current.isEnabled) return
            val text = current.text?.toString()?.trim().orEmpty()
            val desc = current.contentDescription?.toString()?.trim().orEmpty()
            if (text.equals(wanted, true) || desc.equals(wanted, true)) {
                matches += AccessibilityNodeInfo.obtain(current)
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { child ->
                    visit(child)
                    child.recycle()
                }
            }
        }
        visit(node)
        val selected = matches.firstOrNull { !requireClickable || it.isClickable || hasClickableAncestor(it) }
            ?: matches.firstOrNull { !requireClickable }
        matches.filterNot { it === selected }.forEach { it.recycle() }
        return selected
    }

    private fun hasClickableAncestor(node: AccessibilityNodeInfo): Boolean {
        var parent = node.parent
        while (parent != null) {
            val clickable = parent.isClickable
            val next = parent.parent
            parent.recycle()
            if (clickable) {
                next?.recycle()
                return true
            }
            parent = next
        }
        return false
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        // Tenta ação nativa primeiro
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        // Fallback: gesto de toque nas coordenadas do nó
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return tapAt(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    // -----------------------------------------------------
    // AÇÃO: toque genérico em coordenadas (x, y)
    // -----------------------------------------------------
    fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipeAt(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 350): Boolean {
        if (!listOf(x1, y1, x2, y2).all { it.isFinite() }) return false
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(120, 1500)))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    // -----------------------------------------------------
    // AÇÃO: digitar texto num campo focado
    // -----------------------------------------------------
    fun typeText(text: String): Boolean = typeText(text, null)

    fun typeText(text: String, target: String?): Boolean {
        var root = rootInActiveWindow ?: return false
        var focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused == null && !target.isNullOrBlank()) {
            val field = findNodeByText(root, target)
            if (field != null) {
                field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                if (field.isClickable) field.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                field.recycle()
                root.recycle()
                root = rootInActiveWindow ?: return false
                focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            }
        }
        if (focused == null) {
            val candidate = findEditableNode(root)
            if (candidate != null) {
                candidate.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                candidate.recycle()
                root.recycle()
                root = rootInActiveWindow ?: return false
                focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            }
        }
        if (focused == null) {
            root.recycle()
            return false
        }
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text
            )
        }
        val result = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focused.recycle()
        root.recycle()
        return result
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isVisibleToUser && node.isEnabled && node.isEditable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val result = findEditableNode(child)
                child.recycle()
                if (result != null) return result
            }
        }
        return null
    }

    // -----------------------------------------------------
    // AÇÃO: voltar / home (navegação básica do sistema)
    // -----------------------------------------------------
    fun goBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun currentPackage(): String? = lastKnownPackage
}

// ============================================================
// Helper de acesso estático, usado pelo ForegroundService sem
// precisar importar detalhes internos do serviço de Accessibility.
// ============================================================
object JarvisAccessibilityServiceRef {
    fun snapshot(): String = JarvisAccessibilityService.instance?.snapshotScreen() ?: ""
    fun currentPackageName(): String? = JarvisAccessibilityService.instance?.currentPackage()
}
