package com.batttext.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "batttext_settings")

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

    // Status bar icon customization (BatteryGuru defaults to Current mA or Temperature or Level %)
    val statusBarIconMetric: StatusBarIconMetric = StatusBarIconMetric.LEVEL,

    // Individual toggles for BatteryGuru-style 2-line notification (compact, readable, zero redundant Doze state)
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

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_MODE = stringPreferencesKey("font_mode")
        val PURE_ASCII = booleanPreferencesKey("pure_ascii")
        val USE_FAHRENHEIT = booleanPreferencesKey("use_fahrenheit")
        val DESIGN_CAP_OVERRIDE_MAH = intPreferencesKey("design_cap_override_mah")
        val SAMPLE_INTERVAL_MIN = intPreferencesKey("sample_interval_min")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val BG_MONITOR_EN = booleanPreferencesKey("bg_monitor_en")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")

        val STATUS_BAR_ICON_METRIC = stringPreferencesKey("status_bar_icon_metric")
        val NOTIF_SHOW_LEVEL = booleanPreferencesKey("notif_show_level")
        val NOTIF_SHOW_CURRENT = booleanPreferencesKey("notif_show_current")
        val NOTIF_SHOW_POWER = booleanPreferencesKey("notif_show_power")
        val NOTIF_SHOW_TEMP = booleanPreferencesKey("notif_show_temp")
        val NOTIF_SHOW_VOLTAGE = booleanPreferencesKey("notif_show_voltage")
        val NOTIF_SHOW_ETA = booleanPreferencesKey("notif_show_eta")
        val NOTIF_SHOW_ACTIVE_RATE = booleanPreferencesKey("notif_show_active_rate")
        val NOTIF_SHOW_IDLE_RATE = booleanPreferencesKey("notif_show_idle_rate")
        val NOTIF_SHOW_DEEP_SLEEP = booleanPreferencesKey("notif_show_deep_sleep")
        val NOTIF_SHOW_AWAKE_PCT = booleanPreferencesKey("notif_show_awake_pct")

        val ALERT_SOUND = booleanPreferencesKey("alert_sound")
        val ALERT_LIMIT_EN = booleanPreferencesKey("alert_limit_en")
        val ALERT_LIMIT_PCT = intPreferencesKey("alert_limit_pct")
        val ALERT_FULL_EN = booleanPreferencesKey("alert_full_en")
        val ALERT_LOW_EN = booleanPreferencesKey("alert_low_en")
        val ALERT_LOW_PCT = intPreferencesKey("alert_low_pct")
        val ALERT_CRIT_EN = booleanPreferencesKey("alert_crit_en")
        val ALERT_CRIT_PCT = intPreferencesKey("alert_crit_pct")
        val ALERT_TEMP_EN = booleanPreferencesKey("alert_temp_en")
        val ALERT_TEMP_C = intPreferencesKey("alert_temp_c")
        val ALERT_PLUG_CHANGE = booleanPreferencesKey("alert_plug_change")

        val SCREEN_ON_DRAIN_MPCT = intPreferencesKey("screen_on_drain_mpct")
        val SCREEN_OFF_DRAIN_MPCT = intPreferencesKey("screen_off_drain_mpct")
        val SCREEN_ON_DURATION_MS = longPreferencesKey("screen_on_duration_ms")
        val SCREEN_OFF_DURATION_MS = longPreferencesKey("screen_off_duration_ms")
        val DEEP_SLEEP_DURATION_MS = longPreferencesKey("deep_sleep_duration_ms")
        val AWAKE_IDLE_DURATION_MS = longPreferencesKey("awake_idle_duration_ms")
        val SCREEN_RESET_TS = longPreferencesKey("screen_reset_ts")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: "AUTO") }.getOrDefault(ThemeMode.AUTO),
            fontMode = runCatching { FontMode.valueOf(prefs[Keys.FONT_MODE] ?: "SYSTEM") }.getOrDefault(FontMode.SYSTEM),
            pureAsciiBorders = prefs[Keys.PURE_ASCII] ?: false,
            useFahrenheit = prefs[Keys.USE_FAHRENHEIT] ?: false,
            designCapacityOverrideMah = prefs[Keys.DESIGN_CAP_OVERRIDE_MAH] ?: 0,
            sampleIntervalMinutes = prefs[Keys.SAMPLE_INTERVAL_MIN] ?: 10,
            retentionDays = prefs[Keys.RETENTION_DAYS] ?: 14,
            backgroundMonitorEnabled = prefs[Keys.BG_MONITOR_EN] ?: true,
            startOnBoot = prefs[Keys.START_ON_BOOT] ?: true,

            statusBarIconMetric = runCatching {
                StatusBarIconMetric.valueOf(prefs[Keys.STATUS_BAR_ICON_METRIC] ?: "LEVEL")
            }.getOrDefault(StatusBarIconMetric.LEVEL),

            notifShowCurrent = prefs[Keys.NOTIF_SHOW_CURRENT] ?: true,
            notifShowTemp = prefs[Keys.NOTIF_SHOW_TEMP] ?: true,
            notifShowEta = prefs[Keys.NOTIF_SHOW_ETA] ?: true,
            notifShowLevel = prefs[Keys.NOTIF_SHOW_LEVEL] ?: false,
            notifShowPower = prefs[Keys.NOTIF_SHOW_POWER] ?: false,
            notifShowVoltage = prefs[Keys.NOTIF_SHOW_VOLTAGE] ?: false,
            notifShowActiveRate = prefs[Keys.NOTIF_SHOW_ACTIVE_RATE] ?: true,
            notifShowIdleRate = prefs[Keys.NOTIF_SHOW_IDLE_RATE] ?: true,
            notifShowDeepSleepPct = prefs[Keys.NOTIF_SHOW_DEEP_SLEEP] ?: true,
            notifShowAwakePct = prefs[Keys.NOTIF_SHOW_AWAKE_PCT] ?: false,

            alertSoundEnabled = prefs[Keys.ALERT_SOUND] ?: true,
            alertChargeLimitEnabled = prefs[Keys.ALERT_LIMIT_EN] ?: true,
            alertChargeLimitPercent = prefs[Keys.ALERT_LIMIT_PCT] ?: 80,
            alertFullChargeEnabled = prefs[Keys.ALERT_FULL_EN] ?: true,
            alertLowBatteryEnabled = prefs[Keys.ALERT_LOW_EN] ?: true,
            alertLowBatteryPercent = prefs[Keys.ALERT_LOW_PCT] ?: 20,
            alertCriticalBatteryEnabled = prefs[Keys.ALERT_CRIT_EN] ?: true,
            alertCriticalBatteryPercent = prefs[Keys.ALERT_CRIT_PCT] ?: 10,
            alertHighTempEnabled = prefs[Keys.ALERT_TEMP_EN] ?: true,
            alertHighTempCelsius = prefs[Keys.ALERT_TEMP_C] ?: 40,
            alertPlugStateChange = prefs[Keys.ALERT_PLUG_CHANGE] ?: false
        )
    }

    val screenDrainFlow: Flow<ScreenDrainStats> = context.dataStore.data.map { prefs ->
        ScreenDrainStats(
            screenOnDrainPercent = (prefs[Keys.SCREEN_ON_DRAIN_MPCT] ?: 0) / 1000f,
            screenOffDrainPercent = (prefs[Keys.SCREEN_OFF_DRAIN_MPCT] ?: 0) / 1000f,
            screenOnDurationMs = prefs[Keys.SCREEN_ON_DURATION_MS] ?: 0L,
            screenOffDurationMs = prefs[Keys.SCREEN_OFF_DURATION_MS] ?: 0L,
            deepSleepDurationMs = prefs[Keys.DEEP_SLEEP_DURATION_MS] ?: 0L,
            awakeIdleDurationMs = prefs[Keys.AWAKE_IDLE_DURATION_MS] ?: 0L,
            resetTimestampMs = prefs[Keys.SCREEN_RESET_TS] ?: System.currentTimeMillis()
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setFontMode(mode: FontMode) = context.dataStore.edit { it[Keys.FONT_MODE] = mode.name }
    suspend fun setPureAsciiBorders(enabled: Boolean) = context.dataStore.edit { it[Keys.PURE_ASCII] = enabled }
    suspend fun setUseFahrenheit(enabled: Boolean) = context.dataStore.edit { it[Keys.USE_FAHRENHEIT] = enabled }
    suspend fun setDesignCapacityOverrideMah(mah: Int) = context.dataStore.edit {
        it[Keys.DESIGN_CAP_OVERRIDE_MAH] = if (mah <= 0) 0 else mah.coerceIn(1000, 25000)
    }
    suspend fun setSampleIntervalMinutes(minutes: Int) = context.dataStore.edit { it[Keys.SAMPLE_INTERVAL_MIN] = minutes.coerceIn(1, 60) }
    suspend fun setRetentionDays(days: Int) = context.dataStore.edit { it[Keys.RETENTION_DAYS] = days.coerceIn(1, 90) }
    suspend fun setBackgroundMonitorEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.BG_MONITOR_EN] = enabled }
    suspend fun setStartOnBoot(enabled: Boolean) = context.dataStore.edit { it[Keys.START_ON_BOOT] = enabled }

    suspend fun setStatusBarIconMetric(metric: StatusBarIconMetric) = context.dataStore.edit {
        it[Keys.STATUS_BAR_ICON_METRIC] = metric.name
    }
    suspend fun setNotifShowLevel(enabled: Boolean) = context.dataStore.edit { it[Keys.NOTIF_SHOW_LEVEL] = enabled }
    suspend fun setNotifShowCurrent(enabled: Boolean) = context.dataStore.edit { it[Keys.NOTIF_SHOW_CURRENT] = enabled }
    suspend fun setNotifShowPower(enabled: Boolean) = context.dataStore.edit { it[Keys.NOTIF_SHOW_POWER] = enabled }
    suspend fun setNotifShowTemp(enabled: Boolean) = context.dataStore.edit { it[Keys.NOTIF_SHOW_TEMP] = enabled }
    suspend fun setNotifShowVoltage(enabled: Boolean) = context.dataStore.edit { it[Keys.NOTIF_SHOW_VOLTAGE] = enabled }
    suspend fun setNotifShowEta(enabled: Boolean) = context.dataStore.edit { it[Keys.NOTIF_SHOW_ETA] = enabled }
    suspend fun setNotifShowActiveRate(enabled: Boolean) = context.dataStore.edit { it[Keys.NOTIF_SHOW_ACTIVE_RATE] = enabled }
    suspend fun setNotifShowIdleRate(enabled: Boolean) = context.dataStore.edit { it[Keys.NOTIF_SHOW_IDLE_RATE] = enabled }
    suspend fun setNotifShowDeepSleepPct(enabled: Boolean) = context.dataStore.edit { it[Keys.NOTIF_SHOW_DEEP_SLEEP] = enabled }
    suspend fun setNotifShowAwakePct(enabled: Boolean) = context.dataStore.edit { it[Keys.NOTIF_SHOW_AWAKE_PCT] = enabled }

    suspend fun setAlertSoundEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.ALERT_SOUND] = enabled }
    suspend fun setAlertChargeLimitEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.ALERT_LIMIT_EN] = enabled }
    suspend fun setAlertChargeLimitPercent(pct: Int) = context.dataStore.edit { it[Keys.ALERT_LIMIT_PCT] = pct.coerceIn(50, 99) }
    suspend fun setAlertFullChargeEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.ALERT_FULL_EN] = enabled }
    suspend fun setAlertLowBatteryEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.ALERT_LOW_EN] = enabled }
    suspend fun setAlertLowBatteryPercent(pct: Int) = context.dataStore.edit { it[Keys.ALERT_LOW_PCT] = pct.coerceIn(10, 50) }
    suspend fun setAlertCriticalBatteryEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.ALERT_CRIT_EN] = enabled }
    suspend fun setAlertCriticalBatteryPercent(pct: Int) = context.dataStore.edit { it[Keys.ALERT_CRIT_PCT] = pct.coerceIn(3, 25) }
    suspend fun setAlertHighTempEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.ALERT_TEMP_EN] = enabled }
    suspend fun setAlertHighTempCelsius(tempC: Int) = context.dataStore.edit { it[Keys.ALERT_TEMP_C] = tempC.coerceIn(30, 60) }
    suspend fun setAlertPlugStateChange(enabled: Boolean) = context.dataStore.edit { it[Keys.ALERT_PLUG_CHANGE] = enabled }

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

        context.dataStore.edit { prefs ->
            if (!prefs.contains(Keys.SCREEN_RESET_TS)) {
                prefs[Keys.SCREEN_RESET_TS] = System.currentTimeMillis()
            }
            if (isScreenOn) {
                val curDrain = prefs[Keys.SCREEN_ON_DRAIN_MPCT] ?: 0
                val curDur = prefs[Keys.SCREEN_ON_DURATION_MS] ?: 0L
                prefs[Keys.SCREEN_ON_DRAIN_MPCT] = curDrain + (deltaPercent.coerceAtLeast(0) * 1000)
                prefs[Keys.SCREEN_ON_DURATION_MS] = curDur + safeElapsed
            } else {
                val curDrain = prefs[Keys.SCREEN_OFF_DRAIN_MPCT] ?: 0
                val curDur = prefs[Keys.SCREEN_OFF_DURATION_MS] ?: 0L
                val curDeep = prefs[Keys.DEEP_SLEEP_DURATION_MS] ?: 0L
                val curAwake = prefs[Keys.AWAKE_IDLE_DURATION_MS] ?: 0L

                prefs[Keys.SCREEN_OFF_DRAIN_MPCT] = curDrain + (deltaPercent.coerceAtLeast(0) * 1000)
                prefs[Keys.SCREEN_OFF_DURATION_MS] = curDur + safeElapsed
                prefs[Keys.DEEP_SLEEP_DURATION_MS] = curDeep + deepSleepSlice
                prefs[Keys.AWAKE_IDLE_DURATION_MS] = curAwake + safeUptime
            }
        }
    }

    suspend fun resetScreenStats() {
        context.dataStore.edit { prefs ->
            prefs[Keys.SCREEN_ON_DRAIN_MPCT] = 0
            prefs[Keys.SCREEN_OFF_DRAIN_MPCT] = 0
            prefs[Keys.SCREEN_ON_DURATION_MS] = 0L
            prefs[Keys.SCREEN_OFF_DURATION_MS] = 0L
            prefs[Keys.DEEP_SLEEP_DURATION_MS] = 0L
            prefs[Keys.AWAKE_IDLE_DURATION_MS] = 0L
            prefs[Keys.SCREEN_RESET_TS] = System.currentTimeMillis()
        }
    }
}
