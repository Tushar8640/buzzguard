package com.buzzguard.app.model

import java.util.UUID

enum class MatchMode(val label: String) {
    ANY_KEYWORD("Contains any of"),
    ALL_KEYWORDS("Contains all of"),
    NONE_OF_KEYWORDS("Does not contain"),
    REGEX("Matches regex"),
}

enum class VibrationPreset(val label: String, val timings: LongArray) {
    GENTLE("Gentle", longArrayOf(0, 120)),
    DOUBLE_TAP("Double Tap", longArrayOf(0, 120, 100, 120)),
    HEARTBEAT("Heartbeat", longArrayOf(0, 100, 80, 220, 300, 100, 80, 220)),
    RAPID("Rapid", longArrayOf(0, 80, 60, 80, 60, 80, 60, 80)),
    SOS("SOS", longArrayOf(0, 120, 80, 120, 80, 120, 200, 320, 80, 320, 80, 320, 200, 120, 80, 120, 80, 120)),
}

enum class ActionType(val label: String, val shortDescription: String) {
    COOLDOWN("Cooldown", "Mute repeated bursts from the same app"),
    DISMISS("Dismiss", "Auto-swipe unwanted notifications"),
    SNOOZE_BATCH("Snooze / Batch", "Hold and deliver notifications in batches"),
    CUSTOM_ALERT("Custom Alert", "Play custom vibration & sound"),
    REMIND_ME("Remind Me", "Keep reminding until opened"),
    ALARM("Alarm", "Ring loud alarm for urgent alerts"),
    AUTO_REPLY("Auto-Reply", "Send automatic reply if unseen"),
    SECRET("Secret", "Hide sensitive content on lock screen"),
}

data class RuleAction(
    val type: ActionType,
    val durationSeconds: Int = 60,
    val intervalMinutes: Int = 15,
    val maxRepeats: Int = 3,
    val vibrationPreset: VibrationPreset = VibrationPreset.DOUBLE_TAP,
    val soundEnabled: Boolean = true,
    val replyText: String = "I'm busy right now, will reply soon.",
    val placeholderText: String = "New private notification",
) {
    fun describe(): String = when (type) {
        ActionType.COOLDOWN -> "cooldown for ${formatSeconds(durationSeconds)}"
        ActionType.DISMISS -> if (durationSeconds > 0) "dismiss after ${durationSeconds}s" else "dismiss immediately"
        ActionType.SNOOZE_BATCH -> "batch every ${intervalMinutes}m"
        ActionType.CUSTOM_ALERT -> "custom alert (${vibrationPreset.label}${if (soundEnabled) " + sound" else ""})"
        ActionType.REMIND_ME -> "remind every ${intervalMinutes}m (max $maxRepeats×)"
        ActionType.ALARM -> "trigger alarm"
        ActionType.AUTO_REPLY -> "auto-reply \"$replyText\" after ${durationSeconds}s"
        ActionType.SECRET -> "hide content as \"$placeholderText\""
    }

    private fun formatSeconds(sec: Int): String = when {
        sec < 60 -> "${sec}s"
        sec % 60 == 0 -> "${sec / 60}m"
        else -> "${sec / 60}m ${sec % 60}s"
    }
}

enum class RingerFilter(val label: String) {
    ANY("Any mode"),
    NORMAL("Sound on"),
    VIBRATE_OR_SILENT("Silent / Vibrate"),
}

data class DeviceContext(
    val isScreenInteractive: Boolean = true,
    val isSilentOrVibrate: Boolean = false,
    val isOnWifi: Boolean = false,
)

data class NotificationRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val packageNames: List<String> = emptyList(),
    val appLabels: List<String> = emptyList(),
    val matchMode: MatchMode = MatchMode.ANY_KEYWORD,
    val keywords: List<String> = emptyList(),
    val excludeKeywords: List<String> = emptyList(),
    val contactFilter: String = "",
    val scheduleEnabled: Boolean = false,
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 18,
    val endMinute: Int = 0,
    // Calendar.SUNDAY (1) .. Calendar.SATURDAY (7); empty = every day
    val activeDays: Set<Int> = emptySet(),
    val onlyWhenScreenOff: Boolean = false,
    val onlyOnWifi: Boolean = false,
    val ringerFilter: RingerFilter = RingerFilter.ANY,
    val actions: List<RuleAction> = listOf(RuleAction(ActionType.COOLDOWN)),
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toPlainEnglish(): String {
        val parts = mutableListOf<String>()
        val appsText = when {
            appLabels.isNotEmpty() -> appLabels.joinToString(", ")
            packageNames.isNotEmpty() -> packageNames.joinToString(", ")
            else -> "any app"
        }
        parts += "When I get a notification from $appsText"

        if (contactFilter.isNotBlank()) {
            parts += "from \"$contactFilter\""
        }
        if (keywords.isNotEmpty()) {
            val kw = keywords.joinToString(", ") { "\"$it\"" }
            parts += when (matchMode) {
                MatchMode.ANY_KEYWORD -> "containing any of $kw"
                MatchMode.ALL_KEYWORDS -> "containing all of $kw"
                MatchMode.NONE_OF_KEYWORDS -> "not containing $kw"
                MatchMode.REGEX -> "matching regex $kw"
            }
        }
        if (excludeKeywords.isNotEmpty()) {
            val ex = excludeKeywords.joinToString(", ") { "\"$it\"" }
            parts += "excluding $ex"
        }
        if (scheduleEnabled) {
            val daysLabel = if (activeDays.isEmpty() || activeDays.size == 7) "" else " (${formatDays(activeDays)})"
            parts += String.format("between %02d:%02d–%02d:%02d%s", startHour, startMinute, endHour, endMinute, daysLabel)
        }
        if (onlyWhenScreenOff) parts += "while screen is off"
        if (onlyOnWifi) parts += "on Wi-Fi"
        if (ringerFilter != RingerFilter.ANY) parts += "when ringer is ${ringerFilter.label.lowercase()}"

        val actionText = if (actions.isEmpty()) "do nothing" else actions.joinToString(" + ") { it.describe() }
        return "${parts.joinToString(" ")}, then $actionText."
    }

    /**
     * Specificity score used for automatic conflict resolution when multiple rules match:
     * more specific criteria (specific app, contact, keywords, schedule, context) rank higher.
     */
    fun specificityScore(): Int {
        var score = priority * 100
        if (packageNames.isNotEmpty()) score += 30
        if (contactFilter.isNotBlank()) score += 25
        if (keywords.isNotEmpty()) score += 20
        if (excludeKeywords.isNotEmpty()) score += 10
        if (scheduleEnabled) score += 10
        if (onlyWhenScreenOff) score += 8
        if (onlyOnWifi) score += 8
        if (ringerFilter != RingerFilter.ANY) score += 8
        return score
    }

    private fun formatDays(days: Set<Int>): String {
        val names = mapOf(1 to "Sun", 2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat")
        return days.sorted().mapNotNull { names[it] }.joinToString("/")
    }
}

data class IncomingNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val subText: String = "",
    val timestamp: Long = System.currentTimeMillis(),
) {
    val combinedContent: String
        get() = listOf(title, text, subText).filter { it.isNotBlank() }.joinToString(" ")
}

data class NotificationRecord(
    val id: Long = 0,
    val notificationKey: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val matchedRuleId: String? = null,
    val matchedRuleName: String? = null,
    val actionSummary: String = "Delivered",
    val category: RecordCategory = RecordCategory.DELIVERED,
    val dismissedByUser: Boolean = false,
)

enum class RecordCategory(val label: String) {
    DELIVERED("Delivered"),
    DISMISSED("Dismissed"),
    COOLDOWN("Cooldown"),
    SNOOZED("Snoozed"),
    ALERTED("Alerted"),
    SECRET("Secret"),
    UNDOABLE("Swiped"),
}

data class RuleEvaluationResult(
    val matchedRule: NotificationRule?,
    val actionsToExecute: List<RuleAction>,
    val suppressedByCooldown: Boolean = false,
    val actionSummary: String = "Delivered",
    val category: RecordCategory = RecordCategory.DELIVERED,
)

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
)
