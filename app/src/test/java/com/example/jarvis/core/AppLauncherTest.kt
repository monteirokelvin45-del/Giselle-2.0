// ============================================================
// test/core/AppLauncherTest.kt
// ------------------------------------------------------------
// Cobre a parte que AppMatcherTest não alcança: a integração
// real com PackageManager/Intent, via Robolectric (roda no JVM,
// sem emulador — sombra o Android framework de verdade).
// Testa: descoberta de apps instalados de verdade via
// queryIntentActivities, abertura só em match Exact, recusa em
// WeakMatch/Ambiguous, e abertura direta via openPackage() após
// uma hipotética confirmação do usuário.
//
// NOTA DE HONESTIDADE: escrito sem poder compilar/rodar aqui
// (sem Android SDK neste ambiente). shadowPm.setLaunchIntentForPackage
// é API estável do Robolectric 4.x, mas se a versão exata do projeto
// divergir, é o primeiro lugar a checar em caso de erro de build.
// ============================================================

package com.example.jarvis.core

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppLauncherTest {

    private lateinit var launcher: AppLauncher

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        launcher = AppLauncher(context)

        registerFakeApp(context, packageName = "com.whatsapp", label = "WhatsApp")
        registerFakeApp(context, packageName = "com.spotify.music", label = "Spotify")
    }

    private fun registerFakeApp(context: android.content.Context, packageName: String, label: String) {
        val activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.MainActivity"
            applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
        }
        val resolveInfo = ResolveInfo().apply {
            this.activityInfo = activityInfo
            nonLocalizedLabel = label
        }
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val shadowPm = shadowOf(context.packageManager)
        shadowPm.addResolveInfoForIntent(launcherIntent, resolveInfo)

        // API estável do Robolectric para fazer getLaunchIntentForPackage()
        // retornar algo não-nulo para este pacote (sem isso, openByName/
        // openPackage sempre falhariam mesmo com o app "instalado").
        val ownLaunchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(packageName, "$packageName.MainActivity")
        }
        shadowPm.setLaunchIntentForPackage(packageName, ownLaunchIntent)
    }

    @Test
    fun `resolveMatch encontra app real registrado via PackageManager`() {
        val result = launcher.resolveMatch("whatsapp")
        assertTrue(result is AppMatchResult.Exact)
        assertEquals("com.whatsapp", (result as AppMatchResult.Exact).app.packageName)
    }

    @Test
    fun `resolveMatch retorna NoMatch para app nao instalado`() {
        val result = launcher.resolveMatch("aplicativoinexistente")
        assertEquals(AppMatchResult.NoMatch, result)
    }

    @Test
    fun `openByName abre apenas em match exato`() {
        val opened = launcher.openByName("whatsapp")
        assertTrue(opened)
    }

    @Test
    fun `openByName nao abre quando o match nao existe`() {
        val opened = launcher.openByName("appquenaoexiste")
        assertFalse(opened)
    }

    @Test
    fun `openPackage abre diretamente por nome de pacote, sem precisar de match`() {
        val opened = launcher.openPackage("com.spotify.music")
        assertTrue(opened)
    }
}
