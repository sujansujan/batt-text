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
import android.graphics.Typeface
import android.os.Build
import android.util.LruCache
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

    // Cache dynamically rendered text status bar icons keyed by their exact short text string
    private val iconCache = object : LruCache<String, IconCompat>(24) {}

    fun createChannels() {
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
     * Builds a clean, uncluttered notification containing ONLY the exact fields enabled by the user.
     * Never shows battery health, capacity, or charging status text.
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

        // Line 1: Live metrics selected by user (e.g., `78% · -340mA · 31.2° · 4h12m`)
        val topParts = mutableListOf<String>()
        if (settings.notifShowLevel) {
            topParts.add("${snapshot.levelPercent}%")
        }
        if (settings.notifShowCurrent) {
            val sign = if (snapshot.currentMa > 0) "+" else ""
            topParts.add("${sign}${snapshot.currentMa}mA")
        }
        if (settings.notifShowPower) {
            topParts.add(String.format(Locale.US, "%.1fW", snapshot.powerWatts))
        }
        if (settings.notifShowTemp) {
            topParts.add(formatTemp(snapshot.temperatureDeciC, settings.useFahrenheit))
        }
        if (settings.notifShowVoltage) {
            topParts.add(String.format(Locale.US, "%.2fV", snapshot.voltageMv / 1000f))
        }
        if (settings.notifShowEta) {
            snapshot.etaMinutes?.let { mins ->
                val h = mins / 60
                val m = mins % 60
                topParts.add("${h}h${String.format(Locale.US, "%02d", m)}m")
            }
        }

        // Line 2: Consumption per hour & Doze metrics selected by user (e.g., `ACT 8.2%/h · IDL 0.5%/h · DOZE OFF`)
        val bottomParts = mutableListOf<String>()
        if (settings.notifShowActiveRate) {
            val actStr = screenStats.activeDrainRatePctPerHour?.let {
                String.format(Locale.US, "%.1f%%/h", it)
            } ?: if (!snapshot.isCharging && snapshot.isScreenOn && snapshot.speedPercentPerHour > 0f) {
                String.format(Locale.US, "%.1f%%/h", snapshot.speedPercentPerHour)
            } else "--%/h"
            bottomParts.add("ACT $actStr")
        }
        if (settings.notifShowIdleRate) {
            val idlStr = screenStats.idleDrainRatePctPerHour?.let {
                String.format(Locale.US, "%.1f%%/h", it)
            } ?: if (!snapshot.isCharging && !snapshot.isScreenOn && snapshot.speedPercentPerHour > 0f) {
                String.format(Locale.US, "%.1f%%/h", snapshot.speedPercentPerHour)
            } else "--%/h"
            bottomParts.add("IDL $idlStr")
        }
        if (settings.notifShowDozeState) {
            val dozeStr = when {
                snapshot.isDeviceIdleDoze -> "DOZE DEEP"
                !snapshot.isScreenOn -> "DOZE LIGHT"
                else -> "DOZE OFF"
            }
            bottomParts.add(dozeStr)
        }
        if (settings.notifShowDeepSleepPct) {
            val bootDeepPct = if (snapshot.bootElapsedRealtimeMs > 0L) {
                (snapshot.bootDeepSleepMs * 100f) / snapshot.bootElapsedRealtimeMs.toFloat()
            } else 0f
            bottomParts.add(String.format(Locale.US, "SLP %.0f%%", bootDeepPct))
        }

        val topLine = topParts.joinToString(" · ")
        val bottomLine = bottomParts.joinToString(" · ")

        // If user only enabled items from one group, promote it to the single title line with no subtitle
        val finalTitle = when {
            topLine.isNotEmpty() -> topLine
            bottomLine.isNotEmpty() -> bottomLine
            else -> "${snapshot.levelPercent}%"
        }
        val finalSubtitle = if (topLine.isNotEmpty() && bottomLine.isNotEmpty()) bottomLine else null

        val builder = NotificationCompat.Builder(context, CHANNEL_MONITOR_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(finalTitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)

        if (finalSubtitle != null) {
            builder.setContentText(finalSubtitle)
        }

        // Dynamic status bar icon based on user's chosen metric
        if (settings.statusBarIconMetric != StatusBarIconMetric.STATIC) {
            runCatching {
                val (mainText, subLabel) = formatStatusBarIconText(snapshot, settings)
                builder.setSmallIcon(getOrCreateTextIcon(mainText, subLabel))
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
     * Renders any chosen metric (e.g., `78` + `%`, `31` + `°C`, `1.8` + `A`, `7.8` + `W`)
     * into a crisp 64x64 status bar icon bitmap.
     */
    private fun getOrCreateTextIcon(mainText: String, unitLabel: String?): IconCompat {
        val cacheKey = "$mainText|${unitLabel.orEmpty()}"
        iconCache.get(cacheKey)?.let { return it }

        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (unitLabel != null) {
            val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                textSize = when (mainText.length) {
                    1, 2 -> 38f
                    3 -> 30f
                    else -> 24f
                }
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                textSize = 16f
            }
            canvas.drawText(mainText, size / 2f, 36f, mainPaint)
            canvas.drawText(unitLabel, size / 2f, 58f, subPaint)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                textSize = when (mainText.length) {
                    1, 2 -> 44f
                    else -> 32f
                }
            }
            val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
            canvas.drawText(mainText, size / 2f, yPos, paint)
        }

        val icon = IconCompat.createWithBitmap(bitmap)
        iconCache.put(cacheKey, icon)
        return icon
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
