package com.batttext.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.batttext.app.BattTextApp

class AlertDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (id != -1) {
            (context.applicationContext as BattTextApp).notificationHelper.cancelNotification(id)
        }
    }

    companion object {
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
