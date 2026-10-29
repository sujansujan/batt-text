package com.batttext.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.batttext.app.BattTextApp
import com.batttext.app.service.BatteryMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as BattTextApp
                val settings = app.settingsRepository.settingsFlow.first()
                if (settings.startOnBoot) {
                    BatteryMonitorService.startService(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
