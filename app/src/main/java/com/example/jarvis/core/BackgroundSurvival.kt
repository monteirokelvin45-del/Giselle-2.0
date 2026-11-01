package com.example.jarvis.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Ajuda a Giselle a sobreviver em segundo plano: whitelist de bateria + arranque automático do fabricante. */
object BackgroundSurvival {
    private const val PREFS = "survival"
    private const val KEY_AUTOSTART_DONE = "autostart_done"

    private val aggressiveOems = setOf(
        "huawei", "honor", "xiaomi", "redmi", "poco", "oppo", "realme", "oneplus",
        "vivo", "tecno", "infinix", "itel", "transsion", "samsung"
    )

    private val autostartTargets = listOf(
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity"
    )

    fun isWhitelisted(ctx: Context): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

    fun needsAutostartStep(): Boolean = Build.MANUFACTURER.lowercase() in aggressiveOems

    fun autostartDone(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTOSTART_DONE, false)
    fun markAutostartDone(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTOSTART_DONE, true).apply()

    /** Pede para ignorar a otimização de bateria; se o sistema recusar o pedido direto, abre a lista geral. */
    fun requestWhitelist(ctx: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(direct) }.onFailure {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /** Abre o ecrã de arranque automático do fabricante; sem ecrã conhecido, abre os detalhes da app. */
    fun openAutostart(ctx: Context) {
        for ((pkg, cls) in autostartTargets) {
            val ok = runCatching {
                ctx.startActivity(Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (ok) return
        }
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
