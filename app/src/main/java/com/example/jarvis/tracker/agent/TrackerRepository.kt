// ============================================================
// tracker/agent/TrackerRepository.kt
// ------------------------------------------------------------
// Mesmo padrão do JarvisRepository do núcleo: ponte reativa
// entre o TrackerOrchestrator e a UI Compose.
// ============================================================

package com.example.jarvis.tracker.agent

import com.example.jarvis.tracker.data.AiEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TrackerRepository {

    private val _insights = MutableStateFlow<List<AiEvent>>(emptyList())
    val insights: StateFlow<List<AiEvent>> = _insights.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun addInsight(event: AiEvent) {
        _insights.value = listOf(event) + _insights.value
    }

    fun setInsights(list: List<AiEvent>) {
        _insights.value = list
    }

    fun markResolvedLocally(eventId: Long, feedback: String) {
        _insights.value = _insights.value.map {
            if (it.id == eventId) it.copy(feedback = feedback) else it
        }
    }

    fun setStatusMessage(message: String?) {
        _statusMessage.value = message
    }
}
