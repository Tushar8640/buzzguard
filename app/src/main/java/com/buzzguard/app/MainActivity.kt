package com.buzzguard.app

import android.Manifest
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buzzguard.app.model.NotificationRule
import com.buzzguard.app.ui.BuzzGuardSplashScreen
import com.buzzguard.app.ui.BuzzGuardViewModel
import com.buzzguard.app.ui.HistoryScreen
import com.buzzguard.app.ui.RuleEditorScreen
import com.buzzguard.app.ui.RulesScreen
import com.buzzguard.app.ui.SettingsScreen
import com.buzzguard.app.ui.theme.BuzzGuardTheme
import com.buzzguard.app.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BuzzGuardEntry(activity = this)
        }
    }
}

@Composable
private fun BuzzGuardEntry(
    activity: ComponentActivity,
    vm: BuzzGuardViewModel = viewModel(),
) {
    val themeMode by vm.themeMode.collectAsState()
    val dynamicColor by vm.dynamicColor.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(isDark) {
        val barStyle = if (isDark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
    }

    BuzzGuardTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
        Box(modifier = Modifier.fillMaxSize()) {
            BuzzGuardRoot(
                vm = vm,
                isDark = isDark,
                onReplaySplash = { showSplash = true },
            )
            AnimatedVisibility(
                visible = showSplash,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                BuzzGuardSplashScreen(
                    isDark = isDark,
                    onFinished = { showSplash = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuzzGuardRoot(
    vm: BuzzGuardViewModel,
    isDark: Boolean,
    onReplaySplash: () -> Unit,
) {
    val context = LocalContext.current
    val rules by vm.rules.collectAsState()
    val history by vm.history.collectAsState()
    val installedApps by vm.installedApps.collectAsState()
    val listenerEnabled by vm.listenerEnabled.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val dynamicColor by vm.dynamicColor.collectAsState()
    val lastSimulation by vm.lastSimulation.collectAsState()
    val pendingBatchCount by vm.pendingBatchCount.collectAsState()
    val backupStatus by vm.backupStatus.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingRule by remember { mutableStateOf<NotificationRule?>(null) }
    var isCreatingRule by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(vm.exportRulesJson().toByteArray())
                    }
                    vm.setBackupStatus("Rules exported.")
                }.onFailure {
                    vm.setBackupStatus("Export failed: ${it.message}")
                }
            }
        },
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (!json.isNullOrBlank()) vm.importRulesJson(json)
                }.onFailure {
                    vm.setBackupStatus("Import failed: ${it.message}")
                }
            }
        },
    )

    if (isCreatingRule || editingRule != null) {
        RuleEditorScreen(
            existingRule = editingRule,
            installedApps = installedApps,
            isDark = isDark,
            onCancel = {
                isCreatingRule = false
                editingRule = null
            },
            onSave = { rule ->
                vm.saveRule(rule)
                isCreatingRule = false
                editingRule = null
            },
        )
        return
    }

    val titles = listOf("Rules", "History", "Settings")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Text(
                        text = titles[selectedTab],
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                actions = {
                    IconButton(
                        onClick = { vm.setThemeMode(if (isDark) ThemeMode.LIGHT else ThemeMode.DARK) }
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = if (isDark) "Switch to light theme" else "Switch to dark theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        colors = itemColors,
                        icon = { Icon(Icons.AutoMirrored.Outlined.Rule, contentDescription = "Rules") },
                        label = { Text("Rules") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        colors = itemColors,
                        icon = { Icon(Icons.Outlined.History, contentDescription = "History") },
                        label = { Text("History") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        colors = itemColors,
                        icon = { Icon(Icons.Outlined.Tune, contentDescription = "Settings") },
                        label = { Text("Settings") },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> RulesScreen(
                    rules = rules,
                    isDark = isDark,
                    pendingBatchCount = pendingBatchCount,
                    listenerEnabled = listenerEnabled,
                    onOpenListenerSettings = vm::openListenerSettings,
                    onDeliverBatchesNow = vm::deliverAllBatchesNow,
                    onCreateRule = { isCreatingRule = true },
                    onEditRule = { editingRule = it },
                    onDuplicateRule = vm::duplicateRule,
                    onToggleRule = vm::toggleRule,
                    onDeleteRule = vm::deleteRule,
                )
                1 -> HistoryScreen(
                    history = history,
                    isDark = isDark,
                    onRestore = vm::restoreNotification,
                    onCreateRuleFromRecord = { rec -> editingRule = vm.draftRuleFromRecord(rec) },
                )
                2 -> SettingsScreen(
                    listenerEnabled = listenerEnabled,
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    lastSimulation = lastSimulation,
                    backupStatus = backupStatus,
                    onThemeModeChange = vm::setThemeMode,
                    onDynamicColorChange = vm::setDynamicColor,
                    onReplaySplash = onReplaySplash,
                    onOpenListenerSettings = vm::openListenerSettings,
                    onRequestPostNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onSimulate = vm::simulateNotification,
                    onExportRules = { exportLauncher.launch("buzzguard-rules.json") },
                    onImportRules = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    onClearHistory = vm::clearHistory,
                    onResetStarters = vm::resetStarterRules,
                )
            }
        }
    }
}
