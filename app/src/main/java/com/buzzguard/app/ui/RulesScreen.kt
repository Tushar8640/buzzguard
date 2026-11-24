package com.buzzguard.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buzzguard.app.model.ActionType
import com.buzzguard.app.model.NotificationRule

@Composable
fun RulesScreen(
    rules: List<NotificationRule>,
    isDark: Boolean,
    pendingBatchCount: Int,
    listenerEnabled: Boolean,
    onOpenListenerSettings: () -> Unit,
    onDeliverBatchesNow: () -> Unit,
    onCreateRule: () -> Unit,
    onEditRule: (NotificationRule) -> Unit,
    onDuplicateRule: (NotificationRule) -> Unit,
    onToggleRule: (String, Boolean) -> Unit,
    onDeleteRule: (String) -> Unit,
) {
    val activeCount = rules.count { it.enabled }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateRule,
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New rule") },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!listenerEnabled) {
                item(key = "permission-strip") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.NotificationsOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notification access is off",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    text = "Required to filter notifications on-device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                )
                            }
                            TextButton(onClick = onOpenListenerSettings) {
                                Text(
                                    "Allow",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
            }

            if (pendingBatchCount > 0) {
                item(key = "batch-strip") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "$pendingBatchCount notification${if (pendingBatchCount == 1) "" else "s"} held in batch",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            TextButton(onClick = onDeliverBatchesNow) {
                                Text("Deliver now", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            if (rules.isNotEmpty()) {
                item(key = "summary-line") {
                    Text(
                        text = "$activeCount active · ${rules.size} total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                }
            } else {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Outlined.NotificationsNone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No rules yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Create a rule to cooldown chat bursts, dismiss promos, or batch alerts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(rules, key = { it.id }) { rule ->
                RuleSentenceCard(
                    rule = rule,
                    isDark = isDark,
                    onToggle = { onToggleRule(rule.id, it) },
                    onEdit = { onEditRule(rule) },
                    onDuplicate = { onDuplicateRule(rule) },
                    onDelete = { onDeleteRule(rule.id) },
                )
            }
        }
    }
}

@Composable
private fun RuleSentenceCard(
    rule: NotificationRule,
    isDark: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val primaryAction = rule.actions.firstOrNull()?.type ?: ActionType.COOLDOWN
    var menuOpen by remember { mutableStateOf(false) }

    SectionSurface(
        modifier = Modifier.clickable(onClick = onEdit),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionIconCircle(
                    actionType = primaryAction,
                    isDark = isDark,
                    enabled = rule.enabled,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (rule.enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = buildList {
                        if (rule.actions.size > 1) add("${rule.actions.size} actions")
                        if (rule.priority > 0) add("Priority ${rule.priority}")
                        if (rule.scheduleEnabled) add("Scheduled")
                    }
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                )
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "Rule options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = buildHighlightedRuleSentence(rule = rule, muted = !rule.enabled),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 10.dp),
            )
        }
    }
}
