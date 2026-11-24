package com.buzzguard.app.data

import android.content.Context
import android.content.Intent
import com.buzzguard.app.engine.RuleEngine
import com.buzzguard.app.model.ActionType
import com.buzzguard.app.model.DeviceContext
import com.buzzguard.app.model.IncomingNotification
import com.buzzguard.app.model.InstalledAppInfo
import com.buzzguard.app.model.MatchMode
import com.buzzguard.app.model.NotificationRecord
import com.buzzguard.app.model.NotificationRule
import com.buzzguard.app.model.RecordCategory
import com.buzzguard.app.model.RuleAction
import com.buzzguard.app.model.RuleEvaluationResult
import com.buzzguard.app.model.VibrationPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BuzzGuardRepository(private val context: Context) {

    private val db = BuzzGuardDatabase(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val ruleEngine = RuleEngine()

    private val _rules = MutableStateFlow<List<NotificationRule>>(emptyList())
    val rules: StateFlow<List<NotificationRule>> = _rules.asStateFlow()

    private val _history = MutableStateFlow<List<NotificationRecord>>(emptyList())
    val history: StateFlow<List<NotificationRecord>> = _history.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    init {
        scope.launch {
            val loaded = db.getAllRules()
            if (loaded.isEmpty()) {
                seedStarterRules()
            } else {
                _rules.value = loaded
            }
            _history.value = db.getHistory()
            _installedApps.value = queryInstalledApps()
        }
    }

    fun saveRule(rule: NotificationRule) {
        scope.launch {
            db.upsertRule(rule)
            _rules.value = db.getAllRules()
        }
    }

    fun toggleRule(ruleId: String, enabled: Boolean) {
        val target = _rules.value.firstOrNull { it.id == ruleId } ?: return
        saveRule(target.copy(enabled = enabled))
    }

    fun deleteRule(ruleId: String) {
        scope.launch {
            db.deleteRule(ruleId)
            _rules.value = db.getAllRules()
        }
    }

    fun evaluateNotification(
        notification: IncomingNotification,
        deviceContext: DeviceContext = DeviceContext(),
    ): RuleEvaluationResult {
        return ruleEngine.evaluate(notification, _rules.value, deviceContext)
    }

    fun exportRulesJson(): String = db.exportRulesJson()

    fun importRulesJson(rawJson: String, onComplete: (Int) -> Unit = {}) {
        scope.launch {
            val imported = runCatching { db.importRulesJson(rawJson) }.getOrDefault(0)
            _rules.value = db.getAllRules()
            onComplete(imported)
        }
    }

    fun recordNotification(record: NotificationRecord) {
        scope.launch {
            db.insertHistory(record)
            _history.value = db.getHistory()
        }
    }

    fun clearHistory() {
        scope.launch {
            db.clearHistory()
            _history.value = emptyList()
        }
    }

    fun resetStarterRules() {
        scope.launch {
            _rules.value.forEach { db.deleteRule(it.id) }
            seedStarterRules()
        }
    }

    private fun seedStarterRules() {
        val starters = listOf(
            NotificationRule(
                id = "starter-cooldown",
                name = "Cooldown Chat Spam",
                enabled = false,
                priority = 1,
                matchMode = MatchMode.ANY_KEYWORD,
                actions = listOf(RuleAction(type = ActionType.COOLDOWN, durationSeconds = 60)),
            ),
            NotificationRule(
                id = "starter-promo-dismiss",
                name = "Auto-Dismiss Promos",
                enabled = false,
                priority = 2,
                matchMode = MatchMode.ANY_KEYWORD,
                keywords = listOf("% off", "promo", "flash sale", "discount"),
                actions = listOf(RuleAction(type = ActionType.DISMISS, durationSeconds = 0)),
            ),
            NotificationRule(
                id = "starter-urgent-alarm",
                name = "Emergency Keyword Alarm",
                enabled = false,
                priority = 10,
                matchMode = MatchMode.ANY_KEYWORD,
                keywords = listOf("urgent", "emergency", "security alert"),
                actions = listOf(
                    RuleAction(type = ActionType.ALARM),
                    RuleAction(type = ActionType.REMIND_ME, intervalMinutes = 5, maxRepeats = 3),
                ),
            ),
            NotificationRule(
                id = "starter-batch-digest",
                name = "Hourly Batch Digest",
                enabled = false,
                priority = 0,
                scheduleEnabled = true,
                startHour = 9,
                startMinute = 0,
                endHour = 17,
                endMinute = 0,
                actions = listOf(RuleAction(type = ActionType.SNOOZE_BATCH, intervalMinutes = 60)),
            ),
            NotificationRule(
                id = "starter-secret-otp",
                name = "Hide OTP & Bank Previews",
                enabled = false,
                priority = 5,
                matchMode = MatchMode.ANY_KEYWORD,
                keywords = listOf("OTP", "verification code", "balance"),
                actions = listOf(
                    RuleAction(
                        type = ActionType.SECRET,
                        placeholderText = "Private message — unlock to view",
                    ),
                    RuleAction(
                        type = ActionType.CUSTOM_ALERT,
                        vibrationPreset = VibrationPreset.GENTLE,
                        soundEnabled = false,
                    ),
                ),
            ),
        )
        starters.forEach { db.upsertRule(it) }
        _rules.value = db.getAllRules()
    }

    private fun queryInstalledApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(launcherIntent, 0)
        return resolved
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                InstalledAppInfo(packageName = pkg, label = label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun recordUserSwiped(notification: IncomingNotification) {
        recordNotification(
            NotificationRecord(
                notificationKey = notification.key,
                packageName = notification.packageName,
                appName = notification.appName,
                title = notification.title,
                text = notification.text,
                timestamp = System.currentTimeMillis(),
                actionSummary = "Swiped away (tap Undo to restore)",
                category = RecordCategory.UNDOABLE,
                dismissedByUser = true,
            )
        )
    }
}
