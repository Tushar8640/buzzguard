package com.buzzguard.app.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buzzguard.app.model.ActionType
import com.buzzguard.app.model.InstalledAppInfo
import com.buzzguard.app.model.MatchMode
import com.buzzguard.app.model.NotificationRule
import com.buzzguard.app.model.RingerFilter
import com.buzzguard.app.model.RuleAction
import com.buzzguard.app.model.VibrationPreset
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorScreen(
    existingRule: NotificationRule?,
    installedApps: List<InstalledAppInfo>,
    isDark: Boolean,
    onCancel: () -> Unit,
    onSave: (NotificationRule) -> Unit,
) {
    var name by remember { mutableStateOf(existingRule?.name ?: "") }
    var priority by remember { mutableIntStateOf(existingRule?.priority ?: 0) }
    val selectedPackages = remember {
        mutableStateListOf<String>().apply { addAll(existingRule?.packageNames.orEmpty()) }
    }
    var matchMode by remember { mutableStateOf(existingRule?.matchMode ?: MatchMode.ANY_KEYWORD) }
    var keywordsCsv by remember { mutableStateOf(existingRule?.keywords?.joinToString(", ") ?: "") }
    var excludeCsv by remember { mutableStateOf(existingRule?.excludeKeywords?.joinToString(", ") ?: "") }
    var contactFilter by remember { mutableStateOf(existingRule?.contactFilter ?: "") }
    var scheduleEnabled by remember { mutableStateOf(existingRule?.scheduleEnabled ?: false) }
    var startHour by remember { mutableIntStateOf(existingRule?.startHour ?: 9) }
    var endHour by remember { mutableIntStateOf(existingRule?.endHour ?: 18) }
    val activeDays = remember {
        mutableStateListOf<Int>().apply { addAll(existingRule?.activeDays.orEmpty()) }
    }
    var onlyWhenScreenOff by remember { mutableStateOf(existingRule?.onlyWhenScreenOff ?: false) }
    var onlyOnWifi by remember { mutableStateOf(existingRule?.onlyOnWifi ?: false) }
    var ringerFilter by remember { mutableStateOf(existingRule?.ringerFilter ?: RingerFilter.ANY) }

    val actions = remember {
        mutableStateListOf<RuleAction>().apply {
            if (existingRule != null && existingRule.actions.isNotEmpty()) {
                addAll(existingRule.actions)
            } else {
                add(RuleAction(ActionType.COOLDOWN, durationSeconds = 60))
            }
        }
    }

    var showAppPicker by remember { mutableStateOf(false) }

    val selectedLabels = selectedPackages.map { pkg ->
        installedApps.firstOrNull { it.packageName == pkg }?.label ?: pkg
    }

    val isEditingExisting = existingRule != null && existingRule.id.isNotBlank()
    val previewRule = NotificationRule(
        id = if (isEditingExisting) existingRule!!.id else "preview",
        name = name.ifBlank { "Untitled rule" },
        enabled = existingRule?.enabled ?: true,
        priority = priority,
        packageNames = selectedPackages.toList(),
        appLabels = selectedLabels,
        matchMode = matchMode,
        keywords = keywordsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        excludeKeywords = excludeCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        contactFilter = contactFilter.trim(),
        scheduleEnabled = scheduleEnabled,
        startHour = startHour,
        endHour = endHour,
        activeDays = activeDays.toSet(),
        onlyWhenScreenOff = onlyWhenScreenOff,
        onlyOnWifi = onlyOnWifi,
        ringerFilter = ringerFilter,
        actions = actions.toList(),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(if (!isEditingExisting) "New rule" else "Edit rule") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = actions.isNotEmpty(),
                        onClick = {
                            val finalRule = if (isEditingExisting) {
                                previewRule.copy(id = existingRule!!.id, createdAt = existingRule.createdAt)
                            } else {
                                NotificationRule(
                                    name = previewRule.name,
                                    enabled = true,
                                    priority = previewRule.priority,
                                    packageNames = previewRule.packageNames,
                                    appLabels = previewRule.appLabels,
                                    matchMode = previewRule.matchMode,
                                    keywords = previewRule.keywords,
                                    excludeKeywords = previewRule.excludeKeywords,
                                    contactFilter = previewRule.contactFilter,
                                    scheduleEnabled = previewRule.scheduleEnabled,
                                    startHour = previewRule.startHour,
                                    endHour = previewRule.endHour,
                                    activeDays = previewRule.activeDays,
                                    onlyWhenScreenOff = previewRule.onlyWhenScreenOff,
                                    onlyOnWifi = previewRule.onlyOnWifi,
                                    ringerFilter = previewRule.ringerFilter,
                                    actions = previewRule.actions,
                                )
                            }
                            onSave(finalRule)
                        },
                    ) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Calm typographic Mad-Libs summary (BuzzKill's signature natural sentence)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = buildHighlightedRuleSentence(previewRule),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            // Rule Identity & Target
            item {
                SectionHeading("Trigger")
                SectionSurface {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Rule name") },
                            placeholder = { Text("e.g. Group chat cooldown") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAppPicker = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    Icons.Outlined.Apps,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column {
                                    Text("Apps", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (selectedLabels.isEmpty()) "Any installed app"
                                        else selectedLabels.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Text(
                                "Choose",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        OutlinedTextField(
                            value = contactFilter,
                            onValueChange = { contactFilter = it },
                            label = { Text("Contact or title (optional)") },
                            placeholder = { Text("e.g. Mom, Team Chat") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Keyword matching", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            MatchMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = matchMode == mode,
                                    onClick = { matchMode = mode },
                                    label = { Text(mode.label) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = keywordsCsv,
                            onValueChange = { keywordsCsv = it },
                            label = { Text("Keywords (comma-separated)") },
                            placeholder = { Text("e.g. urgent, OTP, delivery") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = excludeCsv,
                            onValueChange = { excludeCsv = it },
                            label = { Text("Exclude words (optional)") },
                            placeholder = { Text("e.g. newsletter, digest") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Conditions
            item {
                SectionHeading("Conditions")
                SectionSurface {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Schedule", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (scheduleEnabled) String.format("%02d:00 – %02d:00", startHour, endHour) else "Always active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = scheduleEnabled, onCheckedChange = { scheduleEnabled = it })
                        }
                        if (scheduleEnabled) {
                            Text("Starts ${String.format("%02d:00", startHour)}", style = MaterialTheme.typography.labelMedium)
                            Slider(value = startHour.toFloat(), onValueChange = { startHour = it.roundToInt() }, valueRange = 0f..23f, steps = 22)
                            Text("Ends ${String.format("%02d:00", endHour)}", style = MaterialTheme.typography.labelMedium)
                            Slider(value = endHour.toFloat(), onValueChange = { endHour = it.roundToInt() }, valueRange = 0f..23f, steps = 22)
                            val dayItems = listOf(1 to "Sun", 2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                dayItems.forEach { (dayNum, label) ->
                                    val selected = dayNum in activeDays
                                    FilterChip(
                                        selected = selected,
                                        onClick = { if (selected) activeDays.remove(dayNum) else activeDays.add(dayNum) },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Only when screen is off", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = onlyWhenScreenOff, onCheckedChange = { onlyWhenScreenOff = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Only on Wi-Fi", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = onlyOnWifi, onCheckedChange = { onlyOnWifi = it })
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text("Ringer mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RingerFilter.entries.forEach { rf ->
                                FilterChip(
                                    selected = ringerFilter == rf,
                                    onClick = { ringerFilter = rf },
                                    label = { Text(rf.label) },
                                )
                            }
                        }
                        Text("Priority ($priority)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(value = priority.toFloat(), onValueChange = { priority = it.roundToInt() }, valueRange = 0f..10f, steps = 9)
                    }
                }
            }

            // Actions
            item {
                SectionHeading("Actions (select one or more)")
                SectionSurface {
                    FlowRow(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ActionType.entries.forEach { type ->
                            val active = actions.any { it.type == type }
                            FilterChip(
                                selected = active,
                                onClick = {
                                    if (active) actions.removeAll { it.type == type }
                                    else actions.add(RuleAction(type = type))
                                },
                                leadingIcon = {
                                    Icon(type.icon(), contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(type.label) },
                            )
                        }
                    }
                }
            }

            items(actions.indices.toList(), key = { idx -> actions[idx].type.name }) { idx ->
                val action = actions[idx]
                ActionConfigBlock(
                    action = action,
                    isDark = isDark,
                    onUpdate = { updated -> actions[idx] = updated },
                    onRemove = { actions.removeAt(idx) },
                )
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            apps = installedApps,
            selected = selectedPackages.toSet(),
            onToggle = { pkg ->
                if (pkg in selectedPackages) selectedPackages.remove(pkg)
                else selectedPackages.add(pkg)
            },
            onClearAll = { selectedPackages.clear() },
            onDismiss = { showAppPicker = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionConfigBlock(
    action: RuleAction,
    isDark: Boolean,
    onUpdate: (RuleAction) -> Unit,
    onRemove: () -> Unit,
) {
    SectionSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionIconCircle(actionType = action.type, isDark = isDark)
                Column(modifier = Modifier.weight(1f)) {
                    Text(action.type.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        action.type.shortDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove action", modifier = Modifier.size(18.dp))
                }
            }

            when (action.type) {
                ActionType.COOLDOWN -> {
                    Text("Cooldown window: ${action.durationSeconds}s", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = action.durationSeconds.toFloat().coerceIn(10f, 300f),
                        onValueChange = { onUpdate(action.copy(durationSeconds = (it / 10f).roundToInt() * 10)) },
                        valueRange = 10f..300f,
                    )
                }
                ActionType.DISMISS -> {
                    Text(
                        if (action.durationSeconds == 0) "Dismiss immediately" else "Dismiss after ${action.durationSeconds}s",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = action.durationSeconds.toFloat().coerceIn(0f, 60f),
                        onValueChange = { onUpdate(action.copy(durationSeconds = it.roundToInt())) },
                        valueRange = 0f..60f,
                    )
                }
                ActionType.SNOOZE_BATCH -> {
                    Text("Batch interval: ${action.intervalMinutes} min", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = action.intervalMinutes.toFloat().coerceIn(5f, 120f),
                        onValueChange = { onUpdate(action.copy(intervalMinutes = (it / 5f).roundToInt() * 5)) },
                        valueRange = 5f..120f,
                    )
                }
                ActionType.CUSTOM_ALERT -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VibrationPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = action.vibrationPreset == preset,
                                onClick = { onUpdate(action.copy(vibrationPreset = preset)) },
                                label = { Text(preset.label) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Play notification sound", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = action.soundEnabled, onCheckedChange = { onUpdate(action.copy(soundEnabled = it)) })
                    }
                }
                ActionType.REMIND_ME -> {
                    Text("Every ${action.intervalMinutes}m · up to ${action.maxRepeats}×", style = MaterialTheme.typography.labelMedium)
                    Slider(value = action.intervalMinutes.toFloat().coerceIn(1f, 30f), onValueChange = { onUpdate(action.copy(intervalMinutes = it.roundToInt())) }, valueRange = 1f..30f)
                    Slider(value = action.maxRepeats.toFloat().coerceIn(1f, 8f), onValueChange = { onUpdate(action.copy(maxRepeats = it.roundToInt())) }, valueRange = 1f..8f, steps = 6)
                }
                ActionType.ALARM -> {
                    Text(
                        "Uses the device alarm channel with SOS vibration and a Stop button.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ActionType.AUTO_REPLY -> {
                    OutlinedTextField(
                        value = action.replyText,
                        onValueChange = { onUpdate(action.copy(replyText = it)) },
                        label = { Text("Reply text") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Wait ${action.durationSeconds}s before replying", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = action.durationSeconds.toFloat().coerceIn(0f, 120f),
                        onValueChange = { onUpdate(action.copy(durationSeconds = (it / 5f).roundToInt() * 5)) },
                        valueRange = 0f..120f,
                    )
                }
                ActionType.SECRET -> {
                    OutlinedTextField(
                        value = action.placeholderText,
                        onValueChange = { onUpdate(action.copy(placeholderText = it)) },
                        label = { Text("Lock-screen placeholder") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<InstalledAppInfo>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = apps.filter {
        query.isBlank() ||
            it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose apps") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps…") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(app.packageName) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = app.packageName in selected, onCheckedChange = { onToggle(app.packageName) })
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onClearAll) { Text("Any app") } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
