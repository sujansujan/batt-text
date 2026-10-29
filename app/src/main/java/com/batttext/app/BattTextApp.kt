package com.batttext.app

import android.app.Application
import com.batttext.app.data.db.AppDatabase
import com.batttext.app.data.prefs.SettingsRepository
import com.batttext.app.data.repository.BatteryRepository
import com.batttext.app.data.shizuku.HardwareSpecReader
import com.batttext.app.notification.NotificationHelper
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class BattTextApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var hardwareSpecReader: HardwareSpecReader
        private set

    lateinit var batteryRepository: BatteryRepository
        private set

    lateinit var notificationHelper: NotificationHelper
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLogger()

        database = AppDatabase.getInstance(this)
        settingsRepository = SettingsRepository(this)
        hardwareSpecReader = HardwareSpecReader(this)
        batteryRepository = BatteryRepository(
            context = this,
            dao = database.batteryDao(),
            settingsRepository = settingsRepository,
            hardwareSpecReader = hardwareSpecReader
        )
        notificationHelper = NotificationHelper(this)
        notificationHelper.createChannels()
    }

    private fun installCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(filesDir, CRASH_LOG_FILENAME).writeText(sw.toString())
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun readLastCrashLog(): String? {
        return runCatching {
            val file = File(filesDir, CRASH_LOG_FILENAME)
            if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
        }.getOrNull()
    }

    fun clearCrashLog() {
        runCatching {
            File(filesDir, CRASH_LOG_FILENAME).delete()
        }
    }

    companion object {
        private const val CRASH_LOG_FILENAME = "last_crash.txt"

        lateinit var instance: BattTextApp
            private set
    }
}
