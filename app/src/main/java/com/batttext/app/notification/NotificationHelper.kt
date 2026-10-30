package com.batttext.app.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.batttext.app.MainActivity
import com.batttext.app.R
import com.batttext.app.data.prefs.ScreenDrainStats
import com.batttext.app.data.prefs.StatusBarIconMetric
import com.batttext.app.data.prefs.UserSettings
import com.batttext.app.data.repository.BatterySnapshot
import com.batttext.app.receiver.AlertDismissReceiver
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Reusable 64x64 bitmap, canvas, and paints so status-bar icon updates allocate 0 bytes of RAM
    private val iconBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    private val iconCanvas = Canvas(iconBitmap)
    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        textSize = 16f
    }

    private var lastNotificationSignature: String = ""
    private var channelsCreated: Boolean = false

    fun createChannels() {
        if (channelsCreated) return
        runCatching {
            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR_ID,
                context.getString(R.string.channel_monitor_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_monitor_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                context.getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_alerts_desc)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(listOf(monitorChannel, alertsChannel))
            channelsCreated = true
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Builds a clean vertical List View notification (NotificationCompat.InboxStyle)
     * where each enabled metric appears on its own scannable row.
     */
    fun buildOngoingNotification(
        snapshot: BatterySnapshot,
        settings: UserSettings,
        screenStats: ScreenDrainStats
    ): Notification {
        createChannels()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val listRows = ArrayList<String>(10)

        if (settings.notifShowLevel) {
            listRows.add("Level: ${snapshot.levelPercent}%")
        }
        if (settings.notifShowCurrent) {
            val sign = if (snapshot.currentMa > 0) "+" else ""
            listRows.add("Current: ${sign}${snapshot.currentMa} mA")
        }
        if (settings.notifShowPower) {
            listRows.add(String.format(Locale.US, "Power: %.1f W", snapshot.powerWatts))
        }
        if (settings.notifShowTemp) {
            listRows.add("Temp: ${formatTemp(snapshot.temperatureDeciC, settings.useFahrenheit)}")
        }
        if (settings.notifShowVoltage) {
            listRows.add(String.format(Locale.US, "Voltage: %.2f V", snapshot.voltageMv / 1000f))
        }
        if (settings.notifShowEta) {
            snapshot.etaMinutes?.let { mins ->
                val h = mins / 60
                val m = mins % 60
                val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                val label = if (snapshot.isCharging) "Full in" else "Time left"
                listRows.add("$label: $timeStr")
            }
        }
        if (settings.notifShowActiveRate) {
            val actStr = screenStats.activeDrainRatePctPerHour?.let {
                String.format(Locale.US, "%.1f %%/h", it)
            } ?: if (!snapshot.isCharging && snapshot.isScreenOn && snapshot.speedPercentPerHour > 0f) {
                String.format(Locale.US, "%.1f %%/h", snapshot.speedPercentPerHour)
            } else "-- %/h"
            listRows.add("Active drain: $actStr")
        }
        if (settings.notifShowIdleRate) {
            val idlStr = screenStats.idleDrainRatePctPerHour?.let {
                String.format(Locale.US, "%.1f %%/h", it)
            } ?: if (!snapshot.isCharging && !snapshot.isScreenOn && snapshot.speedPercentPerHour > 0f) {
                String.format(Locale.US, "%.1f %%/h", snapshot.speedPercentPerHour)
            } else "-- %/h"
            listRows.add("Idle drain: $idlStr")
        }
        if (settings.notifShowDeepSleepPct) {
            val trackedOffTotalMs = screenStats.deepSleepDurationMs + screenStats.awakeIdleDurationMs
            val sleepPct = if (trackedOffTotalMs > 60_000L) {
                (screenStats.deepSleepDurationMs * 100f) / trackedOffTotalMs.toFloat()
            } else if (snapshot.bootElapsedRealtimeMs > 0L) {
                (snapshot.bootDeepSleepMs * 100f) / snapshot.bootElapsedRealtimeMs.toFloat()
            } else 0f
            listRows.add(String.format(Locale.US, "Deep sleep: %.0f%%", sleepPct))
        }
        if (settings.notifShowAwakePct) {
            val trackedOffTotalMs = screenStats.deepSleepDurationMs + screenStats.awakeIdleDurationMs
            val awakePct = if (trackedOffTotalMs > 60_000L) {
                (screenStats.awakeIdleDurationMs * 100f) / trackedOffTotalMs.toFloat()
            } else if (snapshot.bootElapsedRealtimeMs > 0L) {
                (snapshot.bootAwakeUptimeMs * 100f) / snapshot.bootElapsedRealtimeMs.toFloat()
            } else 0f
            listRows.add(String.format(Locale.US, "Awake idle: %.0f%%", awakePct))
        }

        if (listRows.isEmpty()) {
            listRows.add("Level: ${snapshot.levelPercent}%")
        }

        // First item is the collapsed title; second item (if present) is the collapsed subtitle;
        // Expanding the notification shows every item in a vertical InboxStyle list!
        val collapsedTitle = listRows[0]
        val collapsedSubtitle = listRows.getOrNull(1)

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(collapsedTitle)
        for (i in 1 until listRows.size) {
            inboxStyle.addLine(listRows[i])
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_MONITOR_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(collapsedTitle)
            .setStyle(inboxStyle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)

        if (collapsedSubtitle != null) {
            builder.setContentText(collapsedSubtitle)
        }

        if (settings.statusBarIconMetric != StatusBarIconMetric.STATIC) {
            runCatching {
                val (mainText, subLabel) = formatStatusBarIconText(snapshot, settings)
                builder.setSmallIcon(renderTextIconInPlace(mainText, subLabel))
            }
        }

        return builder.build()
    }

    private fun formatStatusBarIconText(
        snapshot: BatterySnapshot,
        settings: UserSettings
    ): Pair<String, String?> {
        return when (settings.statusBarIconMetric) {
            StatusBarIconMetric.LEVEL -> snapshot.levelPercent.toString() to "%"
            StatusBarIconMetric.TEMPERATURE -> {
                val c = snapshot.temperatureDeciC / 10f
                val tempInt = if (settings.useFahrenheit) (c * 9f / 5f + 32f).roundToInt() else c.roundToInt()
                val unit = if (settings.useFahrenheit) "°F" else "°C"
                tempInt.toString() to unit
            }
            StatusBarIconMetric.CURRENT -> {
                val absMa = abs(snapshot.currentMa)
                if (absMa >= 1000) {
                    String.format(Locale.US, "%.1f", absMa / 1000f) to "A"
                } else {
                    absMa.toString() to "mA"
                }
            }
            StatusBarIconMetric.POWER -> {
                val w = snapshot.powerWatts
                if (w >= 10f) {
                    w.roundToInt().toString() to "W"
                } else {
                    String.format(Locale.US, "%.1f", w) to "W"
                }
            }
            StatusBarIconMetric.STATIC -> "${snapshot.levelPercent}" to null
        }
    }

    fun updateOngoingNotification(
        snapshot: BatterySnapshot,
        settings: UserSettings,
        screenStats: ScreenDrainStats
    ) {
        if (!hasNotificationPermission()) return
        // Deduplicate: skip IPC call to system NotificationManager if nothing visible changed
        val sig = "${snapshot.levelPercent}|${snapshot.currentMa / 10}|${snapshot.temperatureDeciC}|" +
            "${snapshot.etaMinutes}|${screenStats.screenOnDrainPercent}|${screenStats.screenOffDrainPercent}|" +
            "${settings.hashCode()}"
        if (sig == lastNotificationSignature) return
        lastNotificationSignature = sig

        runCatching {
            val notification = buildOngoingNotification(snapshot, settings, screenStats)
            notificationManager.notify(ONGOING_NOTIFICATION_ID, notification)
        }
    }

    fun sendAlertNotification(
        alertId: Int,
        title: String,
        body: String,
        playSound: Boolean
    ) {
        if (!hasNotificationPermission()) return

        runCatching {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                alertId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = Intent(context, AlertDismissReceiver::class.java).apply {
                putExtra(AlertDismissReceiver.EXTRA_NOTIFICATION_ID, alertId)
            }
            val dismissPending = PendingIntent.getBroadcast(
                context,
                alertId + 1000,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                .setSmallIcon(R.drawable.ic_stat_battery)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setSilent(!playSound)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(openPending)
                .addAction(0, context.getString(R.string.action_dismiss), dismissPending)

            notificationManager.notify(alertId, builder.build())
        }
    }

    fun cancelNotification(notificationId: Int) {
        runCatching {
            notificationManager.cancel(notificationId)
        }
    }

    /**
     * Reuses a single 64x64 bitmap buffer in memory to draw the status bar icon with zero GC churn.
     */
    @Synchronized
    private fun renderTextIconInPlace(mainText: String, unitLabel: String?): IconCompat {
        iconCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        if (unitLabel != null) {
            mainPaint.textSize = when (mainText.length) {
                1, 2 -> 38f
                3 -> 30f
                else -> 24f
            }
            iconCanvas.drawText(mainText, 32f, 36f, mainPaint)
            iconCanvas.drawText(unitLabel, 32f, 58f, subPaint)
        } else {
            mainPaint.textSize = when (mainText.length) {
                1, 2 -> 44f
                else -> 32f
            }
            val yPos = 32f - ((mainPaint.descent() + mainPaint.ascent()) / 2f)
            iconCanvas.drawText(mainText, 32f, yPos, mainPaint)
        }

        return IconCompat.createWithBitmap(iconBitmap)
    }

    private fun formatTemp(deciC: Int, useFahrenheit: Boolean): String {
        if (deciC <= 0) return "N/A"
        val c = deciC / 10f
        return if (useFahrenheit) {
            val f = c * 9f / 5f + 32f
            String.format(Locale.US, "%.1f°F", f)
        } else {
            String.format(Locale.US, "%.1f°C", c)
        }
    }

    companion object {
        const val CHANNEL_MONITOR_ID = "batttext_monitor_channel"
        const val CHANNEL_ALERTS_ID = "batttext_alerts_channel"

        const val ONGOING_NOTIFICATION_ID = 1001
        const val ALERT_ID_CHARGE_LIMIT = 2001
        const val ALERT_ID_FULL_CHARGE = 2002
        const val ALERT_ID_LOW_BATTERY = 2003
        const val ALERT_ID_CRITICAL_BATTERY = 2004
        const val ALERT_ID_HIGH_TEMP = 2005
        const val ALERT_ID_PLUG_STATE = 2006
    }
}
