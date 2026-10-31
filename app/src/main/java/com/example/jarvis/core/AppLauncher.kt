// ============================================================
// core/AppLauncher.kt
// ------------------------------------------------------------
// Implementa OPEN_APP de verdade: busca os apps instalados via
// PackageManager e delega a decisão de matching para AppMatcher
// (lógica pura, testável sem Android). Este arquivo fica só com
// a parte que realmente precisa de Context/PackageManager.
// ============================================================

package com.example.jarvis.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class AppLauncher(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /**
     * Tenta abrir um app pelo nome dito pelo usuário/LLM.
     * Só abre direto em match Exact — WeakMatch e Ambiguous retornam
     * false aqui (autonomia total só no caso sem ambiguidade real).
     * Use resolveMatch() se quiser tratar os outros casos (ex: pedir
     * confirmação em vez de simplesmente falhar).
     */
    fun openByName(spokenName: String): Boolean {
        val result = resolveMatch(spokenName)
        val app = (result as? AppMatchResult.Exact)?.app ?: return false
        return launch(app.packageName)
    }

    /** Abre diretamente um pacote já resolvido (usado após confirmação do usuário em caso Ambiguous/WeakMatch). */
    fun openPackage(packageName: String): Boolean = launch(packageName)

    fun resolveMatch(spokenName: String): AppMatchResult =
        AppMatcher.resolve(spokenName, getInstalledLaunchableApps())

    private fun launch(packageName: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getInstalledLaunchableApps(): List<LaunchableApp> {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(mainIntent, 0).map { resolveInfo ->
            LaunchableApp(
                packageName = resolveInfo.activityInfo.packageName,
                label = resolveInfo.loadLabel(pm).toString()
            )
        }
    }
}
