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
import com.batttext.app.data.prefs.NotificationStyle
import com.batttext.app.data.prefs.ScreenDrainStats
import com.batttext.app.data.prefs.UserSettings
import com.batttext.app.data.repository.BatterySnapshot
import com.batttext.app.receiver.AlertDismissReceiver
import java.util.Locale

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val iconCache = object : LruCache<Int, IconCompat>(16) {}

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
     * Builds the silent ongoing notification with:
     * - Title: `78% | CHG AC | +1850mA | 31.2C | 4.21V | 0h42m`
     * - Subtitle (collapsed 2nd line): `ACT 8.4%/h | IDL 0.6%/h | DOZE: OFF`
     * - Expanded BigTextStyle: Clean stats block with zero "BATTTEXT LIVE TELEMETRY" header filler.
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

        val statusSegment = if (snapshot.isCharging && snapshot.plugType != "NONE") {
            "CHG ${snapshot.plugType}"
        } else {
            snapshot.statusText
        }

        val currentSign = if (snapshot.currentMa > 0) "+" else ""
        val currentSegment = "${currentSign}${snapshot.currentMa}mA"
        val tempSegment = formatTemp(snapshot.temperatureDeciC, settings.useFahrenheit)
        val voltSegment = String.format(Locale.US, "%.2fV", snapshot.voltageMv / 1000f)

        val etaSegment = snapshot.etaMinutes?.let { mins ->
            val h = mins / 60
            val m = mins % 60
            if (snapshot.isCharging) "full ${h}h${String.format(Locale.US, "%02d", m)}m"
            else "left ${h}h${String.format(Locale.US, "%02d", m)}m"
        } ?: String.format(Locale.US, "%.1f%%/h", snapshot.speedPercentPerHour)

        val titleLine = "${snapshot.levelPercent}% | $statusSegment | $currentSegment | $tempSegment | $voltSegment | $etaSegment"

        val activeRateStr = screenStats.activeDrainRatePctPerHour?.let {
            String.format(Locale.US, "%.1f%%/h", it)
        } ?: if (!snapshot.isCharging && snapshot.isScreenOn && snapshot.speedPercentPerHour > 0f) {
            String.format(Locale.US, "%.1f%%/h", snapshot.speedPercentPerHour)
        } else "--%/h"

        val idleRateStr = screenStats.idleDrainRatePctPerHour?.let {
            String.format(Locale.US, "%.1f%%/h", it)
        } ?: if (!snapshot.isCharging && !snapshot.isScreenOn && snapshot.speedPercentPerHour > 0f) {
            String.format(Locale.US, "%.1f%%/h", snapshot.speedPercentPerHour)
        } else "--%/h"

        val dozeStatusStr = when {
            snapshot.isDeviceIdleDoze -> "DEEP"
            !snapshot.isScreenOn -> "LIGHT"
            else -> "OFF"
        }

        val summaryLine = "ACT $activeRateStr | IDL $idleRateStr | DOZE: $dozeStatusStr"

        val bootDeepSleepPct = if (snapshot.bootElapsedRealtimeMs > 0L) {
            (snapshot.bootDeepSleepMs * 100f) / snapshot.bootElapsedRealtimeMs.toFloat()
        } else 0f

        val expandedBlock = buildString {
            appendLine(summaryLine)
            appendLine("CURRENT: $currentSegment (AVG ${if (snapshot.avgCurrentMa > 0) "+" else ""}${snapshot.avgCurrentMa}mA) • ${String.format(Locale.US, "%.2fW", snapshot.powerWatts)}")
            appendLine("SLEEP  : ${String.format(Locale.US, "%.1f%%", bootDeepSleepPct)} DEEP SLEEP • DOZE: $dozeStatusStr")
            if (settings.notificationStyle == NotificationStyle.DETAILED) {
                val capStr = snapshot.estimatedFullCapacityMah?.let { "${it}mAh" } ?: "N/A"
                append(
                    String.format(
                        Locale.US,
                        "DRAIN  : ON %.1f%% (%s) | OFF %.1f%% (%s) | CAP %s/%dmAh",
                        screenStats.screenOnDrainPercent,
                        activeRateStr,
                        screenStats.screenOffDrainPercent,
                        idleRateStr,
                        capStr,
                        snapshot.effectiveDesignCapacityMah
                    )
                )
            } else {
                append(
                    String.format(
                        Locale.US,
                        "DRAIN  : ON %.1f%% (%s) | OFF %.1f%% (%s)",
                        screenStats.screenOnDrainPercent,
                        activeRateStr,
                        screenStats.screenOffDrainPercent,
                        idleRateStr
                    )
                )
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_MONITOR_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(titleLine)
            .setContentText(summaryLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedBlock))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)

        if (settings.dynamicPercentIcon) {
            runCatching {
                builder.setSmallIcon(getOrCreatePercentageIcon(snapshot.levelPercent))
            }
        }

        return builder.build()
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

    private fun getOrCreatePercentageIcon(percent: Int): IconCompat {
        val clamped = percent.coerceIn(0, 100)
        iconCache.get(clamped)?.let { return it }

        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val text = clamped.toString()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = when (text.length) {
                1 -> 48f
                2 -> 42f
                else -> 30f
            }
        }

        val barPaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 3f
        }
        canvas.drawLine(4f, 6f, 60f, 6f, barPaint)
        canvas.drawLine(4f, 58f, 60f, 58f, barPaint)

        val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(text, size / 2f, yPos, paint)

        val icon = IconCompat.createWithBitmap(bitmap)
        iconCache.put(clamped, icon)
        return icon
    }

    private fun formatTemp(deciC: Int, useFahrenheit: Boolean): String {
        if (deciC <= 0) return "N/A"
        val c = deciC / 10f
        return if (useFahrenheit) {
            val f = c * 9f / 5f + 32f
            String.format(Locale.US, "%.1fF", f)
        } else {
            String.format(Locale.US, "%.1fC", c)
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
