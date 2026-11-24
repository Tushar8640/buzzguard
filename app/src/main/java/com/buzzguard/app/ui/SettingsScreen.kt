package com.buzzguard.app.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buzzguard.app.model.RuleEvaluationResult
import com.buzzguard.app.ui.theme.ThemeMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    listenerEnabled: Boolean,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    lastSimulation: RuleEvaluationResult?,
    backupStatus: String?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onReplaySplash: () -> Unit,
    onOpenListenerSettings: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onSimulate: (appName: String, pkg: String, title: String, text: String) -> Unit,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
    onClearHistory: () -> Unit,
    onResetStarters: () -> Unit,
) {
    var showSimulator by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionHeading("Appearance")
            SectionSurface {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wallpaper colors", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Use Material You palette from your wallpaper",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PreferenceRow(
                    icon = Icons.Outlined.PlayCircleOutline,
                    title = "Replay launch animation",
                    subtitle = "Preview the BuzzGuard splash screen",
                    onClick = onReplaySplash,
                )
            }
        }

        item {
            SectionHeading("Permissions")
            SectionSurface {
                PreferenceRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = "Notification access",
                    subtitle = if (listenerEnabled) "Enabled" else "Off — tap to allow",
                    subtitleColor = if (listenerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    onClick = onOpenListenerSettings,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PreferenceRow(
                    icon = Icons.Outlined.NotificationsNone,
                    title = "Alert notifications",
                    subtitle = "Allow BuzzGuard to post reminders, batches, and alarms",
                    onClick = onRequestPostNotifications,
                )
            }
        }

        item {
            SectionHeading("Tools & backup")
            SectionSurface {
                PreferenceRow(
                    icon = Icons.Outlined.BugReport,
                    title = "Test a notification",
                    subtitle = lastSimulation?.let {
                        it.matchedRule?.let { r -> "Last test matched \"${r.name}\"" } ?: "Last test: no rule matched"
                    } ?: "Simulate an alert against your enabled rules",
                    onClick = { showSimulator = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PreferenceRow(
                    icon = Icons.Outlined.Upload,
                    title = "Export rules",
                    subtitle = backupStatus ?: "Save all rules to a local JSON file",
                    onClick = onExportRules,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PreferenceRow(
                    icon = Icons.Outlined.Download,
                    title = "Import rules",
                    subtitle = "Restore rules from a JSON backup",
                    onClick = onImportRules,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PreferenceRow(
                    icon = Icons.Outlined.Restore,
                    title = "Reset starter templates",
                    subtitle = "Restore the 5 default example rules",
                    onClick = onResetStarters,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PreferenceRow(
                    icon = Icons.Outlined.DeleteSweep,
                    title = "Clear notification history",
                    subtitle = "Remove all locally logged notifications",
                    onClick = onClearHistory,
                )
            }
        }

        item {
            SectionHeading("Privacy")
            SectionSurface {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Offline by design", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "BuzzGuard has no internet permission. Rules and history stay on your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showSimulator) {
        SimulatorDialog(
            lastSimulation = lastSimulation,
            onSimulate = onSimulate,
            onDismiss = { showSimulator = false },
        )
    }
}

@Composable
private fun PreferenceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    subtitleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SimulatorDialog(
    lastSimulation: RuleEvaluationResult?,
    onSimulate: (appName: String, pkg: String, title: String, text: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var simApp by remember { mutableStateOf("WhatsApp") }
    var simPkg by remember { mutableStateOf("com.whatsapp") }
    var simTitle by remember { mutableStateOf("Team Chat") }
    var simText by remember { mutableStateOf("URGENT: server alert") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test a notification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = simApp,
                    onValueChange = { simApp = it },
                    label = { Text("App name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = simPkg,
                    onValueChange = { simPkg = it },
                    label = { Text("Package") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = simTitle,
                    onValueChange = { simTitle = it },
                    label = { Text("Title / sender") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = simText,
                    onValueChange = { simText = it },
                    label = { Text("Message") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (lastSimulation != null) {
                    Text(
                        text = lastSimulation.matchedRule?.let {
                            "Matched \"${it.name}\" → ${lastSimulation.actionSummary}"
                        } ?: "No enabled rule matched.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        confirmButton = {
            TextButton(onClick = { onSimulate(simApp, simPkg, simTitle, simText) }) {
                Text("Run test", fontWeight = FontWeight.SemiBold)
            }
        },
    )
}
