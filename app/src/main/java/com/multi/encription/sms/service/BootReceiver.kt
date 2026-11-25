package com.multi.encription.sms.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.multi.encription.sms.utils.ConfigManager

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Device rebooted, checking SMS Gateway auto-start")
            val configManager = ConfigManager(context)
            if (configManager.isServerEnabled) {
                Log.i(TAG, "Auto-starting SMS Gateway Service on boot")
                SmsGatewayService.startService(context)
            }
        }
    }
}
