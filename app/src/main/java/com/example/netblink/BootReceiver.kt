package com.example.netblink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs(context).auto) {
            try {
                MonitorService.start(context)
            } catch (_: Throwable) {
            }
        }
    }
}
