package com.example.jarvis.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Retoma o serviço principal após boot; o microfone exige abertura da app pelo usuário. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, JarvisForegroundService::class.java)
            )
        }
    }
}
