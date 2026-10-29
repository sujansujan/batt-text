package com.batttext.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.batttext.app.service.BatteryMonitorService
import com.batttext.app.ui.MainViewModel
import com.batttext.app.ui.components.TextDivider
import com.batttext.app.ui.components.TextModalDialog
import com.batttext.app.ui.components.TextTabBar
import com.batttext.app.ui.screens.DashboardScreen
import com.batttext.app.ui.screens.HistoryScreen
import com.batttext.app.ui.screens.SessionsScreen
import com.batttext.app.ui.screens.SettingsScreen
import com.batttext.app.ui.theme.BattTextTheme
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                viewModel.rescanHardwareWithShizuku()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as BattTextApp
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
            val screenStats by viewModel.screenStats.collectAsStateWithLifecycle()
            val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
            val historyRange by viewModel.historyRange.collectAsStateWithLifecycle()
            val samples by viewModel.historySamples.collectAsStateWithLifecycle()
            val sessions by viewModel.recentSessions.collectAsStateWithLifecycle()

            val lifecycleOwner = LocalLifecycleOwner.current

            // Start foreground service only if enabled in settings
            LaunchedEffect(settings.backgroundMonitorEnabled) {
                if (settings.backgroundMonitorEnabled) {
                    BatteryMonitorService.startService(this@MainActivity)
                }
            }

            // Refresh current reading ONLY while the Activity is actively RESUMED on screen (every 5s),
            // using 0% CPU the instant you switch apps or turn off the screen!
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    // If Shizuku is running and not yet granted, prompt once automatically
                    if (app.hardwareSpecReader.isShizukuRunning() && !app.hardwareSpecReader.isShizukuPermissionGranted()) {
                        app.hardwareSpecReader.requestShizukuPermission()
                    }
                    while (true) {
                        viewModel.refreshLiveSnapshot()
                        delay(5000L)
                    }
                }
            }

            var hasNotifPerm by remember {
                mutableStateOf(app.notificationHelper.hasNotificationPermission())
            }
            var showNotifExplainModal by remember { mutableStateOf(false) }
            var showDozeExplainModal by remember { mutableStateOf(false) }
            var lastCrashLog by remember { mutableStateOf(app.readLastCrashLog()) }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        hasNotifPerm = app.notificationHelper.hasNotificationPermission()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val notifLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                hasNotifPerm = granted
                if (granted && settings.backgroundMonitorEnabled) {
                    BatteryMonitorService.startService(this@MainActivity)
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPerm) {
                    showNotifExplainModal = true
                }
            }

            BattTextTheme(
                themeMode = settings.themeMode,
                fontMode = settings.fontMode,
                pureAscii = settings.pureAsciiBorders
            ) {
                val colors = BattTextTheme.colors
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    // Clean top navigation tabs (no app name, clock, or footer clutter)
                    TextTabBar(
                        tabs = listOf("DASH", "HISTORY", "SESSIONS", "SETTINGS"),
                        selectedIndex = selectedTab,
                        onSelect = viewModel::selectTab
                    )
                    TextDivider(doubleLine = false, color = colors.border)
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                    ) {
                        when (selectedTab) {
                            0 -> DashboardScreen(
                                snapshot = snapshot,
                                settings = settings,
                                screenStats = screenStats,
                                hasNotificationPermission = hasNotifPerm,
                                isIgnoringBatteryOptimizations = viewModel.isIgnoringBatteryOptimizations(),
                                onRequestNotificationPermission = { showNotifExplainModal = true },
                                onRequestBatteryOptExemption = { showDozeExplainModal = true },
                                onRequestShizukuOrRescan = viewModel::rescanHardwareWithShizuku,
                                onResetScreenStats = viewModel::resetScreenStats
                            )
                            1 -> HistoryScreen(
                                samples = samples,
                                liveSnapshot = snapshot,
                                settings = settings,
                                selectedRange = historyRange,
                                onSelectRange = viewModel::setHistoryRange
                            )
                            2 -> SessionsScreen(
                                sessions = sessions,
                                settings = settings
                            )
                            3 -> SettingsScreen(
                                snapshot = snapshot,
                                settings = settings,
                                settingsRepository = viewModel.settingsRepository,
                                hasNotificationPermission = hasNotifPerm,
                                isIgnoringBatteryOptimizations = viewModel.isIgnoringBatteryOptimizations(),
                                onToggleBackgroundMonitor = viewModel::toggleBackgroundMonitor,
                                onCycleThemeMode = viewModel::cycleThemeMode,
                                onCycleFontMode = viewModel::cycleFontMode,
                                onCycleStatusBarIconMetric = viewModel::cycleStatusBarIconMetric,
                                onRequestNotificationPermission = { showNotifExplainModal = true },
                                onRequestBatteryOptExemption = { showDozeExplainModal = true },
                                onRequestShizukuOrRescan = viewModel::rescanHardwareWithShizuku,
                                onClearHistory = viewModel::clearAllHistory
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                if (lastCrashLog != null) {
                    TextModalDialog(
                        title = "PREVIOUS CRASH LOG",
                        bodyLines = lastCrashLog!!.lines().take(12),
                        confirmLabel = "CLEAR LOG",
                        onConfirm = {
                            app.clearCrashLog()
                            lastCrashLog = null
                        },
                        dismissLabel = "CLOSE",
                        onDismiss = { lastCrashLog = null }
                    )
                }

                if (showNotifExplainModal) {
                    TextModalDialog(
                        title = "POST_NOTIFICATIONS PERMISSION",
                        bodyLines = listOf(
                            "Allow notifications to show live battery stats, active/idle drain per hour, and doze status in your notification shade."
                        ),
                        confirmLabel = "ALLOW NOTIFICATIONS",
                        onConfirm = {
                            showNotifExplainModal = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        dismissLabel = "LATER",
                        onDismiss = { showNotifExplainModal = false }
                    )
                }

                if (showDozeExplainModal) {
                    TextModalDialog(
                        title = "BATTERY OPTIMIZATION EXEMPTION",
                        bodyLines = listOf(
                            "Exempting BattText prevents HyperOS from killing the passive broadcast receiver.",
                            "BattText unregisters battery listeners when your screen is off and unplugged, using 0% CPU during standby."
                        ),
                        confirmLabel = "OPEN EXEMPTION PROMPT",
                        onConfirm = {
                            showDozeExplainModal = false
                            requestIgnoreBatteryOptimizations()
                        },
                        dismissLabel = "SKIP",
                        onDismiss = { showDozeExplainModal = false }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        super.onDestroy()
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }.onFailure {
            runCatching {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(fallback)
            }
        }
    }
}
