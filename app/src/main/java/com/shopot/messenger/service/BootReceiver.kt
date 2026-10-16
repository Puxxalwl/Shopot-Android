package com.shopot.messenger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

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
            Log.i("BootReceiver", "Restarting ShopotService after $action")
            ShopotService.start(context)
        }
    }
}
