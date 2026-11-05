package com.example.jarvis.mascote

import org.junit.Assert.assertEquals
import org.junit.Test

class ElleMaquinaTest {

    @Test fun `comeca parado e ocioso`() {
        val m = ElleMaquina()
        m.iniciar(0)
        assertEquals(EstadoElle.IDLE, m.estado)
        assertEquals(PoseElle.PARADO, m.pose(0))
    }

    @Test fun `pisca a cada 2,5 s por 180 ms`() {
        val m = ElleMaquina()
        m.iniciar(0)
        assertEquals(PoseElle.PARADO, m.pose(2_499))
        assertEquals(PoseElle.PISCANDO, m.pose(2_500))
        assertEquals(PoseElle.PISCANDO, m.pose(2_679))
        assertEquals(PoseElle.PARADO, m.pose(2_680))
    }

    @Test fun `dorme depois de 3 s sem interacao`() {
        val m = ElleMaquina()
        m.iniciar(0)
        m.pose(2_999)
        assertEquals(EstadoElle.IDLE, m.estado)
        assertEquals(PoseElle.DORMINDO, m.pose(3_000))
        assertEquals(EstadoElle.SLEEP, m.estado)
    }

    @Test fun `toque acorda e reinicia o relogio de sono`() {
        val m = ElleMaquina()
        m.iniciar(0)
        m.pose(3_000) // dorme
        m.tocar(3_100)
        assertEquals(EstadoElle.IDLE, m.estado)
        assertEquals(PoseElle.PISCANDO, m.pose(3_100)) // reação ao toque
        assertEquals(PoseElle.PARADO, m.pose(5_000))
        assertEquals(PoseElle.DORMINDO, m.pose(6_100)) // 3 s depois do toque
    }

    @Test fun `falar mostra pose de fala e nao dorme`() {
        val m = ElleMaquina()
        m.iniciar(0)
        m.falar(true, 1_000)
        assertEquals(EstadoElle.TALK, m.estado)
        assertEquals(PoseElle.FALANDO, m.pose(10_000))
        assertEquals(EstadoElle.TALK, m.estado)
    }

    @Test fun `ao terminar de falar o sono conta a partir do fim`() {
        val m = ElleMaquina()
        m.iniciar(0)
        m.falar(true, 1_000)
        m.falar(false, 8_000)
        assertEquals(EstadoElle.IDLE, m.estado)
        m.pose(10_999)
        assertEquals(EstadoElle.IDLE, m.estado)
        assertEquals(PoseElle.DORMINDO, m.pose(11_000))
    }

    @Test fun `falar falso fora do modo fala nao muda nada`() {
        val m = ElleMaquina()
        m.iniciar(0)
        m.falar(false, 500)
        assertEquals(EstadoElle.IDLE, m.estado)
        assertEquals(PoseElle.DORMINDO, m.pose(3_000))
    }

    @Test fun `falar acorda o mascote que dormia`() {
        val m = ElleMaquina()
        m.iniciar(0)
        m.pose(3_000)
        m.falar(true, 4_000)
        assertEquals(EstadoElle.TALK, m.estado)
        assertEquals(PoseElle.FALANDO, m.pose(4_000))
    }
}
