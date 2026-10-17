package com.stille.messenger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.stille.messenger.ui.SetupPreferences

/**
 * Boot receiver: restart the foreground service after device reboot
 * so shopot-core daemon resumes and can deliver queued messages.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_REBOOT) {
            if (!SetupPreferences(context).completed) return
            Log.i("BootReceiver", "Restarting StilleService after $action")
            StilleService.start(context)
        }
    }
}
