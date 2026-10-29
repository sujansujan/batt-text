package com.batttext.app.ui

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batttext.app.BattTextApp
import com.batttext.app.data.db.BatterySampleEntity
import com.batttext.app.data.db.BatterySessionEntity
import com.batttext.app.data.prefs.FontMode
import com.batttext.app.data.prefs.NotificationStyle
import com.batttext.app.data.prefs.ScreenDrainStats
import com.batttext.app.data.prefs.ThemeMode
import com.batttext.app.data.prefs.UserSettings
import com.batttext.app.data.repository.BatterySnapshot
import com.batttext.app.service.BatteryMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryRange(val hours: Int, val label: String) {
    HOURS_24(24, "24H"),
    DAYS_7(168, "7D")
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BattTextApp
    private val repository = app.batteryRepository
    val settingsRepository = app.settingsRepository
    private val hardwareSpecReader = app.hardwareSpecReader

    val settings: StateFlow<UserSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val screenStats: StateFlow<ScreenDrainStats> = settingsRepository.screenDrainFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScreenDrainStats())

    val snapshot: StateFlow<BatterySnapshot> = repository.snapshot

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _historyRange = MutableStateFlow(HistoryRange.HOURS_24)
    val historyRange: StateFlow<HistoryRange> = _historyRange.asStateFlow()

    val historySamples: StateFlow<List<BatterySampleEntity>> = _historyRange
        .flatMapLatest { range ->
            val since = System.currentTimeMillis() - (range.hours * 3_600_000L)
            repository.observeSamplesSince(since)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSessions: StateFlow<List<BatterySessionEntity>> = repository.observeSessions(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Discover hardware specs once on IO thread at startup
        viewModelScope.launch(Dispatchers.IO) {
            repository.refreshHardwareSpecs(settings.value.designCapacityOverrideMah)
        }
    }

    fun refreshLiveSnapshot() {
        repository.updateFromIntent(null, settings.value.designCapacityOverrideMah)
    }

    fun rescanHardwareWithShizuku() {
        viewModelScope.launch(Dispatchers.IO) {
            if (hardwareSpecReader.isShizukuRunning() && !hardwareSpecReader.isShizukuPermissionGranted()) {
                hardwareSpecReader.requestShizukuPermission()
            } else {
                repository.refreshHardwareSpecs(settings.value.designCapacityOverrideMah)
            }
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index.coerceIn(0, 3)
    }

    fun setHistoryRange(range: HistoryRange) {
        _historyRange.value = range
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        return runCatching {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(app.packageName)
        }.getOrDefault(true)
    }

    fun toggleBackgroundMonitor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBackgroundMonitorEnabled(enabled)
            if (enabled) {
                BatteryMonitorService.startService(app)
            } else {
                BatteryMonitorService.stopService(app)
            }
        }
    }

    fun resetScreenStats() {
        viewModelScope.launch {
            settingsRepository.resetScreenStats()
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    fun cycleThemeMode() {
        viewModelScope.launch {
            val next = when (settings.value.themeMode) {
                ThemeMode.AUTO -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.AUTO
            }
            settingsRepository.setThemeMode(next)
        }
    }

    fun cycleFontMode() {
        viewModelScope.launch {
            val next = when (settings.value.fontMode) {
                FontMode.SYSTEM -> FontMode.MONOSPACE
                FontMode.MONOSPACE -> FontMode.SYSTEM
            }
            settingsRepository.setFontMode(next)
        }
    }

    fun cycleNotificationStyle() {
        viewModelScope.launch {
            val next = when (settings.value.notificationStyle) {
                NotificationStyle.COMPACT -> NotificationStyle.DETAILED
                NotificationStyle.DETAILED -> NotificationStyle.COMPACT
            }
            settingsRepository.setNotificationStyle(next)
        }
    }
}
