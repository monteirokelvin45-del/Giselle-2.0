package com.example.jarvis.updates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GiselleUpdaterTest {

    private lateinit var ctx: Context
    private lateinit var updater: GiselleUpdater

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("giselle_updates", Context.MODE_PRIVATE).edit().clear().commit()
        File(ctx.filesDir, "config").deleteRecursively()
        updater = GiselleUpdater(ctx)
    }

    private fun sha(b: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun commit(version: Int, body: String) {
        val tmp = File(ctx.cacheDir, "cfg$version.tmp").apply { writeText(body) }
        updater.commitConfig(tmp, ConfigRelease(version, "", sha(body.toByteArray()), ""))
    }

    @Test fun `nao oferece config ja instalada nem a rejeitada`() {
        assertTrue(shouldOfferConfig(candidate = 8, installed = 7, rejected = 0))
        assertFalse(shouldOfferConfig(candidate = 7, installed = 7, rejected = 0))
        assertFalse(shouldOfferConfig(candidate = 8, installed = 6, rejected = 8))
        assertTrue(shouldOfferConfig(candidate = 9, installed = 6, rejected = 8))
    }

    @Test fun `rollback memoriza a versao ruim e nao a oferece de novo`() {
        commit(1, """{"schema":1,"systemPrompt":"a"}""")
        commit(2, """{"schema":1,"systemPrompt":"b"}""")
        updater.rollback()
        assertEquals(1, updater.installedConfigVersion())
        assertEquals(2, updater.rejectedConfigVersion())
        assertEquals("a", updater.currentConfig()?.getString("systemPrompt"))
        assertFalse(shouldOfferConfig(2, updater.installedConfigVersion(), updater.rejectedConfigVersion()))
    }

    @Test fun `rejeita hash errado, schema errado e chaves protegidas`() {
        val body = """{"schema":1}"""
        val tmp = File(ctx.cacheDir, "x.tmp").apply { writeText(body) }
        runCatching { updater.commitConfig(tmp, ConfigRelease(1, "", "00", "")) }
            .also { assertTrue(it.isFailure) }
        runCatching { commit(1, """{"schema":2}""") }.also { assertTrue(it.isFailure) }
        runCatching { commit(1, """{"schema":1,"safetyGate":{}}""") }.also { assertTrue(it.isFailure) }
        assertNull(updater.currentConfig())
    }

    @Test fun `tres arranques falhados com config pendente fazem rollback`() {
        commit(1, """{"schema":1}""")
        commit(2, """{"schema":1,"systemPrompt":"b"}""")
        updater.onAppStart(); updater.onAppStart(); updater.onAppStart()
        assertEquals(1, updater.installedConfigVersion())
        assertEquals(2, updater.rejectedConfigVersion())
    }

    @Test fun `ciclo saudavel cancela o rollback`() {
        commit(1, """{"schema":1}""")
        updater.onAppStart(); updater.markHealthy()
        updater.onAppStart(); updater.onAppStart(); updater.onAppStart()
        assertEquals(1, updater.installedConfigVersion())
        assertNotNull(updater.currentConfig())
    }
}
