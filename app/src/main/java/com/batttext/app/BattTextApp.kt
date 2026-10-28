package com.batttext.app

import android.app.Application
import com.batttext.app.data.db.AppDatabase
import com.batttext.app.data.prefs.SettingsRepository
import com.batttext.app.data.repository.BatteryRepository
import com.batttext.app.notification.NotificationHelper

class BattTextApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var batteryRepository: BatteryRepository
        private set

    lateinit var notificationHelper: NotificationHelper
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        settingsRepository = SettingsRepository(this)
        batteryRepository = BatteryRepository(this, database.batteryDao(), settingsRepository)
        notificationHelper = NotificationHelper(this)
        notificationHelper.createChannels()
    }

    companion object {
        lateinit var instance: BattTextApp
            private set
    }
}
