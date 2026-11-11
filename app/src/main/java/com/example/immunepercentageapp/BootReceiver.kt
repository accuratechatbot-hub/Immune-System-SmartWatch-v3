package com.example.immunepercentageapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Restarts monitoring automatically after the watch reboots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, HealthMonitorService::class.java))
        } catch (e: Exception) {
            // The system may refuse a background start; the app will resume when opened.
        }
    }
}
