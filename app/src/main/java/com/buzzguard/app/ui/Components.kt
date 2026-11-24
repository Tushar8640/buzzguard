package com.buzzguard.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.buzzguard.app.model.ActionType
import com.buzzguard.app.model.MatchMode
import com.buzzguard.app.model.NotificationRule
import com.buzzguard.app.model.RecordCategory
import com.buzzguard.app.model.RingerFilter
import com.buzzguard.app.ui.theme.ForestDark
import com.buzzguard.app.ui.theme.ForestLight
import com.buzzguard.app.ui.theme.OchreDark
import com.buzzguard.app.ui.theme.OchreLight
import com.buzzguard.app.ui.theme.SlateBlueDark
import com.buzzguard.app.ui.theme.SlateBlueLight
import com.buzzguard.app.ui.theme.TerracottaDark
import com.buzzguard.app.ui.theme.TerracottaLight

@Composable
fun ActionType.toneColor(isDark: Boolean): Color = when (this) {
    ActionType.COOLDOWN -> if (isDark) ForestDark else ForestLight
    ActionType.DISMISS -> if (isDark) TerracottaDark else TerracottaLight
    ActionType.SNOOZE_BATCH -> if (isDark) SlateBlueDark else SlateBlueLight
    ActionType.CUSTOM_ALERT -> if (isDark) OchreDark else OchreLight
    ActionType.REMIND_ME -> if (isDark) OchreDark else OchreLight
    ActionType.ALARM -> if (isDark) TerracottaDark else TerracottaLight
    ActionType.AUTO_REPLY -> if (isDark) ForestDark else ForestLight
    ActionType.SECRET -> if (isDark) SlateBlueDark else SlateBlueLight
}

fun ActionType.icon(): ImageVector = when (this) {
    ActionType.COOLDOWN -> Icons.Outlined.HourglassEmpty
    ActionType.DISMISS -> Icons.Outlined.DeleteSweep
    ActionType.SNOOZE_BATCH -> Icons.Outlined.Schedule
    ActionType.CUSTOM_ALERT -> Icons.Outlined.Vibration
    ActionType.REMIND_ME -> Icons.Outlined.NotificationsActive
    ActionType.ALARM -> Icons.Outlined.Alarm
    ActionType.AUTO_REPLY -> Icons.AutoMirrored.Outlined.Reply
    ActionType.SECRET -> Icons.Outlined.Lock
}

@Composable
fun RecordCategory.toneColor(isDark: Boolean): Color = when (this) {
    RecordCategory.DELIVERED -> MaterialTheme.colorScheme.onSurfaceVariant
    RecordCategory.DISMISSED -> if (isDark) TerracottaDark else TerracottaLight
    RecordCategory.COOLDOWN -> if (isDark) ForestDark else ForestLight
    RecordCategory.SNOOZED -> if (isDark) SlateBlueDark else SlateBlueLight
    RecordCategory.ALERTED -> if (isDark) OchreDark else OchreLight
    RecordCategory.SECRET -> if (isDark) SlateBlueDark else SlateBlueLight
    RecordCategory.UNDOABLE -> if (isDark) OchreDark else OchreLight
}

/**
 * Clean native Material 3 grouped container: crisp solid surface, subtle 1dp hairline border.
 */
@Composable
fun SectionSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 6.dp, top = 4.dp, bottom = 6.dp),
    )
}

@Composable
fun ActionIconCircle(
    actionType: ActionType,
    isDark: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val tint = if (enabled) actionType.toneColor(isDark) else MaterialTheme.colorScheme.onSurfaceVariant
    val bg = if (enabled) tint.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = actionType.icon(),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
    }
}

/**
 * Builds BuzzKill's signature natural-language rule sentence with highlighted dynamic tokens
 * instead of cluttered nested boxes.
 */
@Composable
fun buildHighlightedRuleSentence(
    rule: NotificationRule,
    muted: Boolean = false,
): AnnotatedString {
    val primaryColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val tokenStyle = SpanStyle(
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    val actionStyle = SpanStyle(
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.SemiBold,
    )
    val baseStyle = SpanStyle(color = primaryColor.copy(alpha = if (muted) 0.7f else 0.82f))

    return buildAnnotatedString {
        withStyle(baseStyle) { append("When ") }
        val apps = when {
            rule.appLabels.isNotEmpty() -> rule.appLabels.joinToString(", ")
            rule.packageNames.isNotEmpty() -> rule.packageNames.joinToString(", ")
            else -> "any app"
        }
        withStyle(tokenStyle) { append(apps) }
        withStyle(baseStyle) { append(" notifies") }

        if (rule.contactFilter.isNotBlank()) {
            withStyle(baseStyle) { append(" from ") }
            withStyle(tokenStyle) { append(rule.contactFilter) }
        }

        if (rule.keywords.isNotEmpty()) {
            val connector = when (rule.matchMode) {
                MatchMode.ANY_KEYWORD -> " with "
                MatchMode.ALL_KEYWORDS -> " with all of "
                MatchMode.NONE_OF_KEYWORDS -> " without "
                MatchMode.REGEX -> " matching "
            }
            withStyle(baseStyle) { append(connector) }
            withStyle(tokenStyle) { append(rule.keywords.joinToString(", ")) }
        }

        if (rule.excludeKeywords.isNotEmpty()) {
            withStyle(baseStyle) { append(" except ") }
            withStyle(tokenStyle) { append(rule.excludeKeywords.joinToString(", ")) }
        }

        if (rule.scheduleEnabled) {
            withStyle(baseStyle) { append(" during ") }
            withStyle(tokenStyle) {
                append(String.format("%02d:%02d–%02d:%02d", rule.startHour, rule.startMinute, rule.endHour, rule.endMinute))
            }
        }

        if (rule.onlyWhenScreenOff) {
            withStyle(baseStyle) { append(" while ") }
            withStyle(tokenStyle) { append("screen is off") }
        }
        if (rule.onlyOnWifi) {
            withStyle(baseStyle) { append(" on ") }
            withStyle(tokenStyle) { append("Wi-Fi") }
        }
        if (rule.ringerFilter != RingerFilter.ANY) {
            withStyle(baseStyle) { append(" on ") }
            withStyle(tokenStyle) { append(rule.ringerFilter.label.lowercase()) }
        }

        withStyle(baseStyle) { append(", ") }
        if (rule.actions.isEmpty()) {
            withStyle(baseStyle) { append("do nothing") }
        } else {
            rule.actions.forEachIndexed { i, action ->
                if (i > 0) withStyle(baseStyle) { append(" and ") }
                withStyle(actionStyle) { append(action.describe()) }
            }
        }
        withStyle(baseStyle) { append(".") }
    }
}
