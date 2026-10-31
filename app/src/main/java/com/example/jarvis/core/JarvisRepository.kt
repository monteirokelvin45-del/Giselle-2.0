// ============================================================
// core/JarvisRepository.kt
// ------------------------------------------------------------
// Singleton que expõe o estado do Jarvis como StateFlow.
// O ForegroundService escreve aqui; a UI (Compose) observa.
// Isso evita acoplar a Activity diretamente ao Service via bind,
// que é mais frágil pra esse tipo de fluxo reativo.
// ============================================================

package com.example.jarvis.core

import com.example.jarvis.agent.PendingAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object JarvisRepository {

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _pendingActions = MutableStateFlow<List<PendingAction>>(emptyList())
    val pendingActions: StateFlow<List<PendingAction>> = _pendingActions.asStateFlow()

    private val _feed = MutableStateFlow<List<FeedItem>>(emptyList())
    val feed: StateFlow<List<FeedItem>> = _feed.asStateFlow()

    private val _memories = MutableStateFlow<List<MemoryEntry>>(emptyList())
    val memories: StateFlow<List<MemoryEntry>> = _memories.asStateFlow()

    // -------- Chamados pelo Service --------

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun addPendingAction(action: PendingAction) {
        _pendingActions.value = _pendingActions.value + action
    }

    fun removePendingAction(action: PendingAction) {
        _pendingActions.value = _pendingActions.value.filterNot { it.createdAt == action.createdAt }
    }

    fun pushFeedItem(item: FeedItem) {
        // mantém só os últimos 50 pra não crescer indefinidamente na memória
        _feed.value = (_feed.value + item).takeLast(50)
    }

    fun setMemories(list: List<MemoryEntry>) {
        _memories.value = list
    }

    // -------- Callback de comando: Service assina, UI dispara --------
    // A UI não chama o Orchestrator diretamente (ele mora no Service);
    // em vez disso, dispara um Intent. Isso aqui é só o "espelho" de estado.
}

// -----------------------------------------------------
// Item de feed mostrado na tela principal — histórico legível
// do que o Jarvis pensou/fez, para dar transparência ao usuário.
// -----------------------------------------------------
data class FeedItem(
    val timestamp: Long = System.currentTimeMillis(),
    val text: String,
    val type: FeedItemType
)

enum class FeedItemType { INFO, ACTION_DONE, ACTION_FAILED, CONFIRMATION_ASKED, SPOKEN }
