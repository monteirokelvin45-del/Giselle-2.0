// mascote/ElleMaquina.kt — máquina de estados do mascote "elle" (sem Android: testável em JVM).
package com.example.jarvis.mascote

/** Estados do mascote. */
enum class EstadoElle { IDLE, TALK, SLEEP }

/** Poses (uma imagem por pose em res/drawable-nodpi). */
enum class PoseElle { PARADO, PISCANDO, FALANDO, DORMINDO }

/**
 * Regras:
 *  - IDLE: fica parado e pisca a cada [intervaloPiscar] ms (pose de piscar dura [duracaoPiscar] ms).
 *  - TALK: enquanto a Giselle responde, mostra a pose de fala e não dorme.
 *  - SLEEP: depois de [tempoSono] ms sem interação em IDLE.
 *  - Toque (ou qualquer interação) acorda e reinicia o tempo sem interação.
 *
 * O tempo é sempre passado de fora ([agora] em ms), para o comportamento ser determinístico nos testes.
 */
class ElleMaquina(
    private val tempoSono: Long = 3_000L,
    private val intervaloPiscar: Long = 2_500L,
    private val duracaoPiscar: Long = 180L,
) {
    var estado: EstadoElle = EstadoElle.IDLE
        private set

    private var ultimaInteracao = 0L
    private var proximoPiscar = 0L
    private var piscarAte = -1L

    fun iniciar(agora: Long) {
        estado = EstadoElle.IDLE
        ultimaInteracao = agora
        proximoPiscar = agora + intervaloPiscar
        piscarAte = -1L
    }

    /** Toque no mascote: acorda, reinicia o tempo sem interação e pisca como reação. */
    fun tocar(agora: Long) {
        ultimaInteracao = agora
        if (estado == EstadoElle.SLEEP) estado = EstadoElle.IDLE
        piscarAte = agora + duracaoPiscar
    }

    /** Giselle começou (true) ou terminou (false) de responder. */
    fun falar(ativo: Boolean, agora: Long) {
        if (ativo) {
            estado = EstadoElle.TALK
            ultimaInteracao = agora
        } else if (estado == EstadoElle.TALK) {
            estado = EstadoElle.IDLE
            ultimaInteracao = agora
        }
    }

    /** Pose a mostrar em [agora]. Deve ser chamada periodicamente. */
    fun pose(agora: Long): PoseElle = when (estado) {
        EstadoElle.TALK -> PoseElle.FALANDO
        EstadoElle.SLEEP -> PoseElle.DORMINDO
        EstadoElle.IDLE -> {
            if (agora - ultimaInteracao >= tempoSono) {
                estado = EstadoElle.SLEEP
                PoseElle.DORMINDO
            } else {
                if (agora >= proximoPiscar) {
                    piscarAte = agora + duracaoPiscar
                    proximoPiscar = agora + intervaloPiscar
                }
                if (agora < piscarAte) PoseElle.PISCANDO else PoseElle.PARADO
            }
        }
    }
}
