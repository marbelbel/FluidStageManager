package com.fluidos.stagemanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

/** Starts the already-enabled overlay after reboot; never enables itself without user consent. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!WorkspaceStore.isAutoStartEnabled(context)) return
        if (!Settings.canDrawOverlays(context)) return

        try {
            val service = Intent(context, OverlayDockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service)
            } else {
                context.startService(service)
            }
        } catch (error: Exception) {
            // OEM background-start policies can still block automatic startup.
            Log.w("FluidStageManager", "Automatic dock startup was blocked", error)
        }
    }
}
