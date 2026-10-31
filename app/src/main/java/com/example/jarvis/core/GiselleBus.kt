package com.example.jarvis.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class GiselleState { IDLE, LISTENING, THINKING, EXECUTING, SPEAKING, ERROR }
enum class Face { IDLE, LISTENING, THINKING, SPEAKING, HAPPY, SAD, SURPRISED, SLEEPY, WINK }

/** "Quadro de avisos": voz, orquestrador e overlay só se falam por aqui. */
object GiselleBus {
    private val _state = MutableStateFlow(GiselleState.IDLE)
    val state: StateFlow<GiselleState> = _state
    private val _stateEvents = MutableSharedFlow<GiselleState>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    /** Mantém transições visuais rápidas sem perder o acesso ao estado atual. */
    val stateEvents: SharedFlow<GiselleState> = _stateEvents

    private val _pulses = MutableSharedFlow<Unit>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val pulses: SharedFlow<Unit> = _pulses

    private val _toast = MutableSharedFlow<Face>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val toast: SharedFlow<Face> = _toast

    /** true enquanto uma tarefa multi-passo corre — o TTS não pode voltar a IDLE nesse intervalo. */
    @Volatile var taskActive: Boolean = false

    /** Falas em fila no TTS; o estado só volta a IDLE quando chega a 0 e não há tarefa ativa. */
    val pendingSpeech = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile var mouthOpen: Float = 0f
        private set
    fun setMouthOpen(value: Float) { mouthOpen = value.coerceIn(0f, 1f); pulse() }

    private val _speech = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Texto que a Giselle está a dizer (alimenta o balão de fala). */
    val speech: SharedFlow<String> = _speech
    fun say(t: String) { _speech.tryEmit(t) }

    private val _announce = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Pedidos para a Giselle FALAR em voz alta (chamadas, lembretes). */
    val announce: SharedFlow<String> = _announce
    fun announce(t: String) { _announce.tryEmit(t) }

    private val _notified = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Nova notificação (só reação visual, não fala). */
    val notified: SharedFlow<String> = _notified
    fun notified(t: String) { _notified.tryEmit(t) }

    @Synchronized
    fun set(s: GiselleState) {
        _state.value = s
        _stateEvents.tryEmit(s)
    }
    fun pulse() { _pulses.tryEmit(Unit) }
    fun celebrate(ok: Boolean) { _toast.tryEmit(if (ok) Face.HAPPY else Face.SAD) }
}
