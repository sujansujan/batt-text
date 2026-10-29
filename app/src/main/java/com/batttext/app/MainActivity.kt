package com.batttext.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LaunchedEffect(Unit) {
                BatteryMonitorService.startService(this@MainActivity)
            }
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
            val screenStats by viewModel.screenStats.collectAsStateWithLifecycle()
            val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
            val historyRange by viewModel.historyRange.collectAsStateWithLifecycle()
            val samples by viewModel.historySamples.collectAsStateWithLifecycle()
            val sessions by viewModel.recentSessions.collectAsStateWithLifecycle()
            val clockText by viewModel.clockText.collectAsStateWithLifecycle()

            var hasNotifPerm by remember {
                mutableStateOf((application as BattTextApp).notificationHelper.hasNotificationPermission())
            }
            var showNotifExplainModal by remember { mutableStateOf(false) }
            var showDozeExplainModal by remember { mutableStateOf(false) }

            val notifLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                hasNotifPerm = granted
                if (granted) {
                    BatteryMonitorService.startService(this@MainActivity)
                }
            }

            // Prompt notification explanation once on Android 13+ if not yet granted
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
                val fontFamily = BattTextTheme.fontFamily
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // Top header scoreboard bar: BATTTEXT                 14:32:07
                    HeaderLine(clockText = clockText)
                    TextDivider(doubleLine = true, color = colors.border)

                    // Navigation tabs: DASH | HISTORY | SESSIONS | SETTINGS
                    TextTabBar(
                        tabs = listOf("DASH", "HISTORY", "SESSIONS", "SETTINGS"),
                        selectedIndex = selectedTab,
                        onSelect = viewModel::selectTab
                    )
                    TextDivider(doubleLine = false, color = colors.border)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Active screen body
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
                                settings = settings,
                                settingsRepository = viewModel.settingsRepository,
                                hasNotificationPermission = hasNotifPerm,
                                isIgnoringBatteryOptimizations = viewModel.isIgnoringBatteryOptimizations(),
                                onCycleThemeMode = viewModel::cycleThemeMode,
                                onCycleFontMode = viewModel::cycleFontMode,
                                onCycleNotificationStyle = viewModel::cycleNotificationStyle,
                                onRequestNotificationPermission = { showNotifExplainModal = true },
                                onRequestBatteryOptExemption = { showDozeExplainModal = true },
                                onClearHistory = viewModel::clearAllHistory
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Bottom status strip
                    TextDivider(doubleLine = false, color = colors.border)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "FGS: ACTIVE (NO-WAKELOCK)",
                            color = colors.muted,
                            fontFamily = fontFamily,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "FONT: ${settings.fontMode.name} | THEME: ${settings.themeMode.name}",
                            color = colors.muted,
                            fontFamily = fontFamily,
                            fontSize = 11.sp
                        )
                    }
                }

                if (showNotifExplainModal) {
                    TextModalDialog(
                        title = "POST_NOTIFICATIONS PERMISSION",
                        bodyLines = listOf(
                            "BattText displays a silent, low-importance live telemetry line in your status bar:",
                            "78% | Charging AC | +1850mA | 31.2C | 4.21V",
                            "It also posts one-shot threshold alerts (charge limit, low battery, overheat).",
                            "Allow notifications so the foreground monitor can display live stats?"
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
                            "Android cannot deliver sticky ACTION_BATTERY_CHANGED or ACTION_SCREEN_ON/OFF broadcasts to background services if the system kills the process during Doze.",
                            "BattText uses ZERO wake locks and ZERO internet.",
                            "Exempt BattText from battery optimization for uninterrupted session logging?"
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

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }.onFailure {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(fallback)
        }
    }
}

@Composable
private fun HeaderLine(clockText: String) {
    val colors = BattTextTheme.colors
    val fontFamily = BattTextTheme.fontFamily
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "BATTTEXT",
            color = colors.accent,
            fontFamily = fontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = clockText,
            color = colors.foreground,
            fontFamily = fontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
