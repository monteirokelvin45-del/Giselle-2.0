package com.example.jarvis.mascote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ElleMaquinaTest {

    /** Versão de teste com sono de 3 s (o padrão da classe é 40 s, como na versão final). */
    private fun maquina3s() = ElleMaquina(tempoSono = 3_000L).also { it.iniciar(0) }

    @Test fun `comeca parado e ocioso`() {
        val m = maquina3s()
        assertEquals(EstadoElle.IDLE, m.estado)
        assertEquals(PoseElle.PARADO, m.pose(0))
    }

    @Test fun `pisca a cada 2,5 s por 180 ms`() {
        val m = maquina3s()
        assertEquals(PoseElle.PARADO, m.pose(2_499))
        assertEquals(PoseElle.PISCANDO, m.pose(2_500))
        assertEquals(PoseElle.PISCANDO, m.pose(2_679))
        assertEquals(PoseElle.PARADO, m.pose(2_680))
    }

    @Test fun `dorme depois do tempo de sono sem interacao`() {
        val m = maquina3s()
        m.pose(2_999)
        assertEquals(EstadoElle.IDLE, m.estado)
        assertEquals(PoseElle.DORMINDO, m.pose(3_000))
        assertEquals(EstadoElle.SLEEP, m.estado)
    }

    @Test fun `versao final dorme so depois de 40 s`() {
        val m = ElleMaquina().also { it.iniciar(0) }
        m.pose(39_999)
        assertEquals(EstadoElle.IDLE, m.estado)
        assertEquals(PoseElle.DORMINDO, m.pose(40_000))
    }

    @Test fun `toque acorda e reinicia o relogio de sono`() {
        val m = maquina3s()
        m.pose(3_000) // dorme
        m.tocar(3_100)
        assertEquals(EstadoElle.IDLE, m.estado)
        assertEquals(PoseElle.PISCANDO, m.pose(3_100)) // reação ao toque
        assertEquals(PoseElle.PARADO, m.pose(5_000))
        assertEquals(PoseElle.DORMINDO, m.pose(6_100)) // 3 s depois do toque
    }

    @Test fun `falar alterna a boca a cada 150 ms e nao dorme`() {
        val m = maquina3s()
        m.falar(true, 1_000)
        assertEquals(EstadoElle.TALK, m.estado)
        assertEquals(PoseElle.FALANDO, m.pose(1_000))
        assertEquals(PoseElle.PARADO, m.pose(1_150))
        assertEquals(PoseElle.FALANDO, m.pose(1_300))
        assertEquals(PoseElle.PARADO, m.pose(1_450))
        m.pose(10_000)
        assertEquals(EstadoElle.TALK, m.estado)
    }

    @Test fun `ao terminar de falar o sono conta a partir do fim`() {
        val m = maquina3s()
        m.falar(true, 1_000)
        m.falar(false, 8_000)
        assertEquals(EstadoElle.IDLE, m.estado)
        m.pose(10_999)
        assertEquals(EstadoElle.IDLE, m.estado)
        assertEquals(PoseElle.DORMINDO, m.pose(11_000))
    }

    @Test fun `falar falso fora do modo fala nao muda nada`() {
        val m = maquina3s()
        m.falar(false, 500)
        assertEquals(EstadoElle.IDLE, m.estado)
        assertEquals(PoseElle.DORMINDO, m.pose(3_000))
    }

    @Test fun `falar acorda o mascote que dormia`() {
        val m = maquina3s()
        m.pose(3_000)
        m.falar(true, 4_000)
        assertEquals(EstadoElle.TALK, m.estado)
        assertEquals(PoseElle.FALANDO, m.pose(4_000))
    }

    @Test fun `pensando pisca mais rapido`() {
        val m = maquina3s()
        m.pensando(true, 0)
        assertEquals(PoseElle.PISCANDO, m.pose(1_200))
        assertEquals(PoseElle.PARADO, m.pose(1_400))
        assertEquals(PoseElle.PISCANDO, m.pose(2_400))
    }

    @Test fun `pensando nao deixa dormir`() {
        val m = maquina3s()
        m.pensando(true, 0)
        m.pose(10_000)
        assertEquals(EstadoElle.IDLE, m.estado)
        assertNotEquals(PoseElle.DORMINDO, m.pose(10_000))
    }

    @Test fun `ao terminar de pensar volta ao ritmo normal e pode dormir`() {
        val m = maquina3s()
        m.pensando(true, 0)
        m.pensando(false, 5_000)
        assertEquals(PoseElle.PARADO, m.pose(6_000))
        assertEquals(PoseElle.DORMINDO, m.pose(8_000))
    }
}
