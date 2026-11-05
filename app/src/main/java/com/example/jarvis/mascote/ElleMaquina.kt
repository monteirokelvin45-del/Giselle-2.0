// mascote/ElleMaquina.kt — máquina de estados do mascote "elle" (sem Android: testável em JVM).
package com.example.jarvis.mascote

/** Estados do mascote. */
enum class EstadoElle { IDLE, TALK, SLEEP }

/** Poses (uma imagem por pose em res/drawable-nodpi). */
enum class PoseElle { PARADO, PISCANDO, FALANDO, DORMINDO }

/**
 * Regras:
 *  - IDLE: fica parado e pisca a cada [intervaloPiscar] ms (pose de piscar dura [duracaoPiscar] ms).
 *  - "Pensando" (ocupado): mesma pose parada, mas pisca mais rápido ([intervaloPiscarPensando]) e não dorme.
 *  - TALK: enquanto a Giselle fala, alterna a boca: pose de fala e parada a cada [periodoBoca] ms.
 *  - SLEEP: depois de [tempoSono] ms sem interação em IDLE.
 *  - Toque ou digitação (interação) acorda e reinicia o tempo sem interação.
 *
 * O tempo é sempre passado de fora ([agora] em ms), para o comportamento ser determinístico nos testes.
 */
class ElleMaquina(
    private val tempoSono: Long = 40_000L,
    private val intervaloPiscar: Long = 2_500L,
    private val intervaloPiscarPensando: Long = 1_200L,
    private val duracaoPiscar: Long = 180L,
    private val periodoBoca: Long = 150L,
) {
    var estado: EstadoElle = EstadoElle.IDLE
        private set

    private var ultimaInteracao = 0L
    private var proximoPiscar = 0L
    private var piscarAte = -1L
    private var inicioFala = 0L
    private var ocupado = false

    private fun intervaloAtual(): Long = if (ocupado) intervaloPiscarPensando else intervaloPiscar

    fun iniciar(agora: Long) {
        estado = EstadoElle.IDLE
        ultimaInteracao = agora
        proximoPiscar = agora + intervaloPiscar
        piscarAte = -1L
        ocupado = false
    }

    /** Toque ou digitação: acorda e reinicia o tempo sem interação. */
    fun tocar(agora: Long) {
        ultimaInteracao = agora
        if (estado == EstadoElle.SLEEP) estado = EstadoElle.IDLE
        piscarAte = agora + duracaoPiscar
    }

    /** Giselle começou (true) ou terminou (false) de falar. */
    fun falar(ativo: Boolean, agora: Long) {
        if (ativo) {
            if (estado != EstadoElle.TALK) inicioFala = agora
            estado = EstadoElle.TALK
            ultimaInteracao = agora
        } else if (estado == EstadoElle.TALK) {
            estado = EstadoElle.IDLE
            ultimaInteracao = agora
        }
    }

    /** Giselle está ocupada (ouvindo, pensando ou executando): pisca mais rápido e não dorme. */
    fun pensando(ativo: Boolean, agora: Long) {
        ocupado = ativo
        ultimaInteracao = agora
        proximoPiscar = agora + intervaloAtual()
    }

    /** Pose a mostrar em [agora]. Deve ser chamada periodicamente. */
    fun pose(agora: Long): PoseElle = when (estado) {
        EstadoElle.TALK -> {
            val fase = ((agora - inicioFala) / periodoBoca) % 2L
            if (fase == 0L) PoseElle.FALANDO else PoseElle.PARADO
        }
        EstadoElle.SLEEP -> PoseElle.DORMINDO
        EstadoElle.IDLE -> {
            if (!ocupado && agora - ultimaInteracao >= tempoSono) {
                estado = EstadoElle.SLEEP
                PoseElle.DORMINDO
            } else {
                if (agora >= proximoPiscar) {
                    piscarAte = agora + duracaoPiscar
                    proximoPiscar = agora + intervaloAtual()
                }
                if (agora < piscarAte) PoseElle.PISCANDO else PoseElle.PARADO
            }
        }
    }
}
