package com.batttext.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { AUTO, DARK, AMOLED, LIGHT }
enum class FontMode { SYSTEM, MONOSPACE }

/**
 * Which live value to render as plain-text digits in the status bar icon.
 */
enum class StatusBarIconMetric(val label: String) {
    CURRENT("CURRENT mA"),
    LEVEL("LEVEL %"),
    TEMPERATURE("TEMP"),
    POWER("WATTS"),
    STATIC("STATIC [███]")
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val fontMode: FontMode = FontMode.SYSTEM,
    val pureAsciiBorders: Boolean = false,
    val useFahrenheit: Boolean = false,
    val designCapacityOverrideMah: Int = 0,
    val sampleIntervalMinutes: Int = 10,
    val retentionDays: Int = 14,
    val backgroundMonitorEnabled: Boolean = true,
    val startOnBoot: Boolean = true,

    val statusBarIconMetric: StatusBarIconMetric = StatusBarIconMetric.LEVEL,

    // Individual toggles for Vertical List View notification items
    val notifShowCurrent: Boolean = true,
    val notifShowTemp: Boolean = true,
    val notifShowEta: Boolean = true,
    val notifShowLevel: Boolean = false,
    val notifShowPower: Boolean = false,
    val notifShowVoltage: Boolean = false,
    val notifShowActiveRate: Boolean = true,
    val notifShowIdleRate: Boolean = true,
    val notifShowDeepSleepPct: Boolean = true,
    val notifShowAwakePct: Boolean = false,

    // Threshold alerts
    val alertSoundEnabled: Boolean = true,
    val alertChargeLimitEnabled: Boolean = true,
    val alertChargeLimitPercent: Int = 80,
    val alertFullChargeEnabled: Boolean = true,
    val alertLowBatteryEnabled: Boolean = true,
    val alertLowBatteryPercent: Int = 20,
    val alertCriticalBatteryEnabled: Boolean = true,
    val alertCriticalBatteryPercent: Int = 10,
    val alertHighTempEnabled: Boolean = true,
    val alertHighTempCelsius: Int = 40,
    val alertPlugStateChange: Boolean = false
)

data class ScreenDrainStats(
    val screenOnDrainPercent: Float = 0f,
    val screenOffDrainPercent: Float = 0f,
    val screenOnDurationMs: Long = 0L,
    val screenOffDurationMs: Long = 0L,
    val deepSleepDurationMs: Long = 0L,
    val awakeIdleDurationMs: Long = 0L,
    val resetTimestampMs: Long = System.currentTimeMillis()
) {
    val activeDrainRatePctPerHour: Float?
        get() {
            val hours = screenOnDurationMs / 3_600_000f
            return if (hours >= 0.03f) screenOnDrainPercent / hours else null
        }

    val idleDrainRatePctPerHour: Float?
        get() {
            val hours = screenOffDurationMs / 3_600_000f
            return if (hours >= 0.03f) screenOffDrainPercent / hours else null
        }
}

/**
 * Zero-overhead SharedPreferences-backed repository.
 * Eliminates the 500KB DataStore + Protobuf dependency and saves ~3 MB of runtime RAM.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("batttext_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<UserSettings> = _settingsFlow.asStateFlow()

    private val _screenDrainFlow = MutableStateFlow(loadScreenDrain())
    val screenDrainFlow: StateFlow<ScreenDrainStats> = _screenDrainFlow.asStateFlow()

    private fun loadSettings(): UserSettings {
        return UserSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", "AUTO") ?: "AUTO") }.getOrDefault(ThemeMode.AUTO),
            fontMode = runCatching { FontMode.valueOf(prefs.getString("font_mode", "SYSTEM") ?: "SYSTEM") }.getOrDefault(FontMode.SYSTEM),
            pureAsciiBorders = prefs.getBoolean("pure_ascii", false),
            useFahrenheit = prefs.getBoolean("use_fahrenheit", false),
            designCapacityOverrideMah = prefs.getInt("design_cap_override_mah", 0),
            sampleIntervalMinutes = prefs.getInt("sample_interval_min", 10),
            retentionDays = prefs.getInt("retention_days", 14),
            backgroundMonitorEnabled = prefs.getBoolean("bg_monitor_en", true),
            startOnBoot = prefs.getBoolean("start_on_boot", true),
            statusBarIconMetric = runCatching {
                StatusBarIconMetric.valueOf(prefs.getString("status_bar_icon_metric", "LEVEL") ?: "LEVEL")
            }.getOrDefault(StatusBarIconMetric.LEVEL),
            notifShowCurrent = prefs.getBoolean("notif_show_current", true),
            notifShowTemp = prefs.getBoolean("notif_show_temp", true),
            notifShowEta = prefs.getBoolean("notif_show_eta", true),
            notifShowLevel = prefs.getBoolean("notif_show_level", false),
            notifShowPower = prefs.getBoolean("notif_show_power", false),
            notifShowVoltage = prefs.getBoolean("notif_show_voltage", false),
            notifShowActiveRate = prefs.getBoolean("notif_show_active_rate", true),
            notifShowIdleRate = prefs.getBoolean("notif_show_idle_rate", true),
            notifShowDeepSleepPct = prefs.getBoolean("notif_show_deep_sleep", true),
            notifShowAwakePct = prefs.getBoolean("notif_show_awake_pct", false),
            alertSoundEnabled = prefs.getBoolean("alert_sound", true),
            alertChargeLimitEnabled = prefs.getBoolean("alert_limit_en", true),
            alertChargeLimitPercent = prefs.getInt("alert_limit_pct", 80),
            alertFullChargeEnabled = prefs.getBoolean("alert_full_en", true),
            alertLowBatteryEnabled = prefs.getBoolean("alert_low_en", true),
            alertLowBatteryPercent = prefs.getInt("alert_low_pct", 20),
            alertCriticalBatteryEnabled = prefs.getBoolean("alert_crit_en", true),
            alertCriticalBatteryPercent = prefs.getInt("alert_crit_pct", 10),
            alertHighTempEnabled = prefs.getBoolean("alert_temp_en", true),
            alertHighTempCelsius = prefs.getInt("alert_temp_c", 40),
            alertPlugStateChange = prefs.getBoolean("alert_plug_change", false)
        )
    }

    private fun loadScreenDrain(): ScreenDrainStats {
        return ScreenDrainStats(
            screenOnDrainPercent = prefs.getInt("screen_on_drain_mpct", 0) / 1000f,
            screenOffDrainPercent = prefs.getInt("screen_off_drain_mpct", 0) / 1000f,
            screenOnDurationMs = prefs.getLong("screen_on_duration_ms", 0L),
            screenOffDurationMs = prefs.getLong("screen_off_duration_ms", 0L),
            deepSleepDurationMs = prefs.getLong("deep_sleep_duration_ms", 0L),
            awakeIdleDurationMs = prefs.getLong("awake_idle_duration_ms", 0L),
            resetTimestampMs = prefs.getLong("screen_reset_ts", System.currentTimeMillis())
        )
    }

    private inline fun updatePref(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _settingsFlow.value = loadSettings()
    }

    suspend fun setThemeMode(mode: ThemeMode) = updatePref { putString("theme_mode", mode.name) }
    suspend fun setFontMode(mode: FontMode) = updatePref { putString("font_mode", mode.name) }
    suspend fun setPureAsciiBorders(enabled: Boolean) = updatePref { putBoolean("pure_ascii", enabled) }
    suspend fun setUseFahrenheit(enabled: Boolean) = updatePref { putBoolean("use_fahrenheit", enabled) }
    suspend fun setDesignCapacityOverrideMah(mah: Int) = updatePref {
        putInt("design_cap_override_mah", if (mah <= 0) 0 else mah.coerceIn(1000, 25000))
    }
    suspend fun setSampleIntervalMinutes(minutes: Int) = updatePref { putInt("sample_interval_min", minutes.coerceIn(1, 60)) }
    suspend fun setRetentionDays(days: Int) = updatePref { putInt("retention_days", days.coerceIn(1, 90)) }
    suspend fun setBackgroundMonitorEnabled(enabled: Boolean) = updatePref { putBoolean("bg_monitor_en", enabled) }
    suspend fun setStartOnBoot(enabled: Boolean) = updatePref { putBoolean("start_on_boot", enabled) }

    suspend fun setStatusBarIconMetric(metric: StatusBarIconMetric) = updatePref {
        putString("status_bar_icon_metric", metric.name)
    }
    suspend fun setNotifShowLevel(enabled: Boolean) = updatePref { putBoolean("notif_show_level", enabled) }
    suspend fun setNotifShowCurrent(enabled: Boolean) = updatePref { putBoolean("notif_show_current", enabled) }
    suspend fun setNotifShowPower(enabled: Boolean) = updatePref { putBoolean("notif_show_power", enabled) }
    suspend fun setNotifShowTemp(enabled: Boolean) = updatePref { putBoolean("notif_show_temp", enabled) }
    suspend fun setNotifShowVoltage(enabled: Boolean) = updatePref { putBoolean("notif_show_voltage", enabled) }
    suspend fun setNotifShowEta(enabled: Boolean) = updatePref { putBoolean("notif_show_eta", enabled) }
    suspend fun setNotifShowActiveRate(enabled: Boolean) = updatePref { putBoolean("notif_show_active_rate", enabled) }
    suspend fun setNotifShowIdleRate(enabled: Boolean) = updatePref { putBoolean("notif_show_idle_rate", enabled) }
    suspend fun setNotifShowDeepSleepPct(enabled: Boolean) = updatePref { putBoolean("notif_show_deep_sleep", enabled) }
    suspend fun setNotifShowAwakePct(enabled: Boolean) = updatePref { putBoolean("notif_show_awake_pct", enabled) }

    suspend fun setAlertSoundEnabled(enabled: Boolean) = updatePref { putBoolean("alert_sound", enabled) }
    suspend fun setAlertChargeLimitEnabled(enabled: Boolean) = updatePref { putBoolean("alert_limit_en", enabled) }
    suspend fun setAlertChargeLimitPercent(pct: Int) = updatePref { putInt("alert_limit_pct", pct.coerceIn(50, 99)) }
    suspend fun setAlertFullChargeEnabled(enabled: Boolean) = updatePref { putBoolean("alert_full_en", enabled) }
    suspend fun setAlertLowBatteryEnabled(enabled: Boolean) = updatePref { putBoolean("alert_low_en", enabled) }
    suspend fun setAlertLowBatteryPercent(pct: Int) = updatePref { putInt("alert_low_pct", pct.coerceIn(10, 50)) }
    suspend fun setAlertCriticalBatteryEnabled(enabled: Boolean) = updatePref { putBoolean("alert_crit_en", enabled) }
    suspend fun setAlertCriticalBatteryPercent(pct: Int) = updatePref { putInt("alert_crit_pct", pct.coerceIn(3, 25)) }
    suspend fun setAlertHighTempEnabled(enabled: Boolean) = updatePref { putBoolean("alert_temp_en", enabled) }
    suspend fun setAlertHighTempCelsius(tempC: Int) = updatePref { putInt("alert_temp_c", tempC.coerceIn(30, 60)) }
    suspend fun setAlertPlugStateChange(enabled: Boolean) = updatePref { putBoolean("alert_plug_change", enabled) }

    suspend fun recordScreenSlice(
        isScreenOn: Boolean,
        deltaPercent: Int,
        deltaElapsedMs: Long,
        deltaUptimeMs: Long
    ) {
        if (deltaElapsedMs <= 0L && deltaPercent <= 0) return
        val safeElapsed = deltaElapsedMs.coerceAtLeast(0L)
        val safeUptime = deltaUptimeMs.coerceIn(0L, safeElapsed)
        val deepSleepSlice = (safeElapsed - safeUptime).coerceAtLeast(0L)

        prefs.edit().apply {
            if (!prefs.contains("screen_reset_ts")) {
                putLong("screen_reset_ts", System.currentTimeMillis())
            }
            if (isScreenOn) {
                val curDrain = prefs.getInt("screen_on_drain_mpct", 0)
                val curDur = prefs.getLong("screen_on_duration_ms", 0L)
                putInt("screen_on_drain_mpct", curDrain + (deltaPercent.coerceAtLeast(0) * 1000))
                putLong("screen_on_duration_ms", curDur + safeElapsed)
            } else {
                val curDrain = prefs.getInt("screen_off_drain_mpct", 0)
                val curDur = prefs.getLong("screen_off_duration_ms", 0L)
                val curDeep = prefs.getLong("deep_sleep_duration_ms", 0L)
                val curAwake = prefs.getLong("awake_idle_duration_ms", 0L)

                putInt("screen_off_drain_mpct", curDrain + (deltaPercent.coerceAtLeast(0) * 1000))
                putLong("screen_off_duration_ms", curDur + safeElapsed)
                putLong("deep_sleep_duration_ms", curDeep + deepSleepSlice)
                putLong("awake_idle_duration_ms", curAwake + safeUptime)
            }
        }.apply()
        _screenDrainFlow.value = loadScreenDrain()
    }

    suspend fun resetScreenStats() {
        prefs.edit().apply {
            putInt("screen_on_drain_mpct", 0)
            putInt("screen_off_drain_mpct", 0)
            putLong("screen_on_duration_ms", 0L)
            putLong("screen_off_duration_ms", 0L)
            putLong("deep_sleep_duration_ms", 0L)
            putLong("awake_idle_duration_ms", 0L)
            putLong("screen_reset_ts", System.currentTimeMillis())
        }.apply()
        _screenDrainFlow.value = loadScreenDrain()
    }
}
