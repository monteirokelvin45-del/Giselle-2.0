package com.example.jarvis.visual

import com.example.jarvis.core.Face
import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarStateMappingTest {

    @Test
    fun `expressões antigas mapeiam para os estados da spec`() {
        assertEquals(AvatarState.REST, Face.IDLE.toAvatarState())
        assertEquals(AvatarState.LISTENING, Face.LISTENING.toAvatarState())
        assertEquals(AvatarState.LISTENING, Face.SURPRISED.toAvatarState())
        assertEquals(AvatarState.THINKING, Face.THINKING.toAvatarState())
        assertEquals(AvatarState.SPEAKING, Face.SPEAKING.toAvatarState())
        assertEquals(AvatarState.HAPPY, Face.HAPPY.toAvatarState())
        assertEquals(AvatarState.HAPPY, Face.WINK.toAvatarState())
        assertEquals(AvatarState.ERROR, Face.SAD.toAvatarState())
        assertEquals(AvatarState.YAWN, Face.SLEEPY.toAvatarState())
    }

    @Test
    fun `todos os estados da spec existem`() {
        val names = AvatarState.values().map { it.name }.toSet()
        val required = setOf("REST", "LISTENING", "THINKING", "ACTING", "SPEAKING", "SUCCESS", "ERROR", "SLEEP")
        assertEquals(emptySet<String>(), required - names)
    }
}
