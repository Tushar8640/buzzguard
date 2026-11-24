package com.buzzguard.app.engine

import com.buzzguard.app.model.ActionType
import com.buzzguard.app.model.DeviceContext
import com.buzzguard.app.model.IncomingNotification
import com.buzzguard.app.model.MatchMode
import com.buzzguard.app.model.NotificationRule
import com.buzzguard.app.model.RecordCategory
import com.buzzguard.app.model.RingerFilter
import com.buzzguard.app.model.RuleAction
import com.buzzguard.app.model.RuleEvaluationResult
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure Kotlin rule evaluation engine implementing BuzzKill's conditional logic:
 * - App, contact, keyword (ANY / ALL / NONE / REGEX), exclude keywords, schedule + days, and device context filters
 * - Automatic conflict resolution via specificity + manual priority
 * - Cooldown burst tracking per rule + package + conversation title
 */
class RuleEngine(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    // Key: "${ruleId}|${packageName}|${title}" -> last allowed timestamp (ms)
    private val cooldownLastAllowed = ConcurrentHashMap<String, Long>()

    fun evaluate(
        notification: IncomingNotification,
        rules: List<NotificationRule>,
        deviceContext: DeviceContext = DeviceContext(),
    ): RuleEvaluationResult {
        val now = clock()
        val matchingRules = rules
            .filter { it.enabled && matches(notification, it, now, deviceContext) }
            .sortedWith(
                compareByDescending<NotificationRule> { it.specificityScore() }
                    .thenByDescending { it.priority }
                    .thenBy { it.createdAt }
            )

        val winner = matchingRules.firstOrNull()
            ?: return RuleEvaluationResult(
                matchedRule = null,
                actionsToExecute = emptyList(),
                actionSummary = "Delivered",
                category = RecordCategory.DELIVERED,
            )

        val actionsToRun = mutableListOf<RuleAction>()
        var suppressedByCooldown = false
        val summaryParts = mutableListOf<String>()

        for (action in winner.actions) {
            if (action.type == ActionType.COOLDOWN) {
                val cooldownKey = "${winner.id}|${notification.packageName}|${notification.title.lowercase()}"
                val lastAllowed = cooldownLastAllowed[cooldownKey]
                val windowMs = action.durationSeconds.coerceAtLeast(1) * 1000L
                if (lastAllowed != null && (now - lastAllowed) < windowMs) {
                    suppressedByCooldown = true
                    actionsToRun += action
                    summaryParts += "Cooldown active (muted)"
                } else {
                    cooldownLastAllowed[cooldownKey] = now
                    summaryParts += "Cooldown started (${action.durationSeconds}s)"
                }
            } else {
                actionsToRun += action
                summaryParts += action.type.label
            }
        }

        val category = when {
            suppressedByCooldown -> RecordCategory.COOLDOWN
            actionsToRun.any { it.type == ActionType.DISMISS } -> RecordCategory.DISMISSED
            actionsToRun.any { it.type == ActionType.SNOOZE_BATCH } -> RecordCategory.SNOOZED
            actionsToRun.any { it.type == ActionType.SECRET } -> RecordCategory.SECRET
            actionsToRun.any {
                it.type == ActionType.ALARM || it.type == ActionType.CUSTOM_ALERT || it.type == ActionType.REMIND_ME
            } -> RecordCategory.ALERTED
            else -> RecordCategory.DELIVERED
        }

        return RuleEvaluationResult(
            matchedRule = winner,
            actionsToExecute = actionsToRun,
            suppressedByCooldown = suppressedByCooldown,
            actionSummary = summaryParts.joinToString(" · ").ifBlank { "Matched" },
            category = category,
        )
    }

    fun matches(
        notification: IncomingNotification,
        rule: NotificationRule,
        nowMillis: Long = clock(),
        deviceContext: DeviceContext = DeviceContext(),
    ): Boolean {
        // 1. App filter
        if (rule.packageNames.isNotEmpty() && notification.packageName !in rule.packageNames) {
            return false
        }

        // 2. Contact / sender filter (checks notification title)
        if (rule.contactFilter.isNotBlank()) {
            val contact = rule.contactFilter.trim().lowercase()
            if (!notification.title.lowercase().contains(contact)) {
                return false
            }
        }

        val contentLower = notification.combinedContent.lowercase()

        // 3. Exclude keywords ("doesn't contain")
        val cleanedExcludes = rule.excludeKeywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (cleanedExcludes.any { contentLower.contains(it) }) {
            return false
        }

        // 4. Keyword / Regex criteria
        val cleanedKeywords = rule.keywords.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanedKeywords.isNotEmpty()) {
            val keywordMatch = when (rule.matchMode) {
                MatchMode.ANY_KEYWORD -> cleanedKeywords.any { contentLower.contains(it.lowercase()) }
                MatchMode.ALL_KEYWORDS -> cleanedKeywords.all { contentLower.contains(it.lowercase()) }
                MatchMode.NONE_OF_KEYWORDS -> cleanedKeywords.none { contentLower.contains(it.lowercase()) }
                MatchMode.REGEX -> cleanedKeywords.any { pattern ->
                    runCatching { Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(notification.combinedContent) }
                        .getOrDefault(false)
                }
            }
            if (!keywordMatch) return false
        }

        // 5. Schedule window + days of week
        if (rule.scheduleEnabled && !isWithinSchedule(rule, nowMillis)) {
            return false
        }

        // 6. Device contextual state
        if (rule.onlyWhenScreenOff && deviceContext.isScreenInteractive) {
            return false
        }
        if (rule.onlyOnWifi && !deviceContext.isOnWifi) {
            return false
        }
        when (rule.ringerFilter) {
            RingerFilter.ANY -> Unit
            RingerFilter.NORMAL -> if (deviceContext.isSilentOrVibrate) return false
            RingerFilter.VIBRATE_OR_SILENT -> if (!deviceContext.isSilentOrVibrate) return false
        }

        return true
    }

    fun isWithinSchedule(rule: NotificationRule, nowMillis: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        if (rule.activeDays.isNotEmpty()) {
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek !in rule.activeDays) return false
        }
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = rule.startHour * 60 + rule.startMinute
        val endMinutes = rule.endHour * 60 + rule.endMinute

        return if (startMinutes <= endMinutes) {
            nowMinutes in startMinutes..endMinutes
        } else {
            // Overnight schedule (e.g., 22:00 to 07:00)
            nowMinutes >= startMinutes || nowMinutes <= endMinutes
        }
    }

    fun resetCooldowns() {
        cooldownLastAllowed.clear()
    }
}
