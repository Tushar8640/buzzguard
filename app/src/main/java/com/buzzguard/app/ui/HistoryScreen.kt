package com.buzzguard.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buzzguard.app.model.NotificationRecord
import com.buzzguard.app.model.RecordCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    history: List<NotificationRecord>,
    isDark: Boolean,
    onRestore: (NotificationRecord) -> Unit,
    onCreateRuleFromRecord: (NotificationRecord) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<RecordCategory?>(null) }
    var inspecting by remember { mutableStateOf<NotificationRecord?>(null) }

    val filtered = remember(history, query, selectedCategory) {
        history.filter { rec ->
            val matchesCat = selectedCategory == null || rec.category == selectedCategory
            val q = query.trim()
            val matchesQuery = q.isEmpty() ||
                rec.appName.contains(q, ignoreCase = true) ||
                rec.title.contains(q, ignoreCase = true) ||
                rec.text.contains(q, ignoreCase = true) ||
                (rec.matchedRuleName?.contains(q, ignoreCase = true) == true)
            matchesCat && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(19.dp)) },
            placeholder = { Text("Search notifications…") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All") },
                )
            }
            items(RecordCategory.entries) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                    label = { Text(cat.label) },
                )
            }
        }

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "No notifications logged",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Recent notifications and swiped alerts are saved locally on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            ) {
                item {
                    SectionSurface {
                        filtered.forEachIndexed { index, record ->
                            NotificationShadeRow(
                                record = record,
                                isDark = isDark,
                                onClick = { inspecting = record },
                                onRestore = { onRestore(record) },
                            )
                            if (index < filtered.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    inspecting?.let { rec ->
        AlertDialog(
            onDismissRequest = { inspecting = null },
            title = { Text(rec.appName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(rec.title, style = MaterialTheme.typography.titleSmall)
                    if (rec.text.isNotBlank()) {
                        Text(rec.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = rec.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${rec.matchedRuleName?.let { "$it · " } ?: ""}${rec.actionSummary}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onRestore(rec)
                        inspecting = null
                    }
                ) { Text("Restore") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        inspecting = null
                        onCreateRuleFromRecord(rec)
                    }
                ) { Text("Create rule", fontWeight = FontWeight.SemiBold) }
            },
        )
    }
}

@Composable
private fun NotificationShadeRow(
    record: NotificationRecord,
    isDark: Boolean,
    onClick: () -> Unit,
    onRestore: () -> Unit,
) {
    val timeText = remember(record.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(record.timestamp))
    }
    val tone = record.category.toneColor(isDark)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(tone),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${record.appName} · $timeText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = record.category.label,
                style = MaterialTheme.typography.labelSmall,
                color = tone,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = record.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (record.text.isNotBlank()) {
            Text(
                text = record.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (record.dismissedByUser || record.matchedRuleName != null) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = record.matchedRuleName ?: "Swiped away",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Restore",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onRestore),
                )
            }
        }
    }
}
