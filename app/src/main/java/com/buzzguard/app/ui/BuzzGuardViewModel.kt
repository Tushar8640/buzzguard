package com.buzzguard.app.ui

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.buzzguard.app.BuzzGuardApp
import com.buzzguard.app.model.ActionType
import com.buzzguard.app.model.IncomingNotification
import com.buzzguard.app.model.InstalledAppInfo
import com.buzzguard.app.model.MatchMode
import com.buzzguard.app.model.NotificationRecord
import com.buzzguard.app.model.NotificationRule
import com.buzzguard.app.model.RuleAction
import com.buzzguard.app.model.RuleEvaluationResult
import com.buzzguard.app.service.BuzzGuardNotificationListener
import com.buzzguard.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

private val Context.prefsDataStore by preferencesDataStore(name = "buzzguard_prefs")
private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

class BuzzGuardViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BuzzGuardApp
    private val repository = app.repository
    private val executor = app.actionExecutor

    val rules: StateFlow<List<NotificationRule>> = repository.rules
    val history: StateFlow<List<NotificationRecord>> = repository.history
    val installedApps: StateFlow<List<InstalledAppInfo>> = repository.installedApps
    val pendingBatchCount: StateFlow<Int> = executor.pendingBatchCount

    private val _listenerEnabled = MutableStateFlow(BuzzGuardNotificationListener.isEnabled(application))
    val listenerEnabled: StateFlow<Boolean> = _listenerEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(false)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    init {
        viewModelScope.launch {
            app.prefsDataStore.data.collect { prefs ->
                val raw = prefs[KEY_THEME_MODE]
                _themeMode.value = runCatching { ThemeMode.valueOf(raw ?: ThemeMode.SYSTEM.name) }
                    .getOrDefault(ThemeMode.SYSTEM)
                _dynamicColor.value = prefs[KEY_DYNAMIC_COLOR] ?: false
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch {
            app.prefsDataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        viewModelScope.launch {
            app.prefsDataStore.edit { prefs -> prefs[KEY_DYNAMIC_COLOR] = enabled }
        }
    }

    fun duplicateRule(rule: NotificationRule) {
        saveRule(
            rule.copy(
                id = UUID.randomUUID().toString(),
                name = "${rule.name} (copy)",
                enabled = false,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    private val _lastSimulation = MutableStateFlow<RuleEvaluationResult?>(null)
    val lastSimulation: StateFlow<RuleEvaluationResult?> = _lastSimulation.asStateFlow()

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()

    fun refreshPermissions() {
        _listenerEnabled.value = BuzzGuardNotificationListener.isEnabled(app)
    }

    fun openListenerSettings() {
        BuzzGuardNotificationListener.openSettings(app)
    }

    fun saveRule(rule: NotificationRule) = repository.saveRule(rule)

    fun toggleRule(ruleId: String, enabled: Boolean) = repository.toggleRule(ruleId, enabled)

    fun deleteRule(ruleId: String) = repository.deleteRule(ruleId)

    fun clearHistory() = repository.clearHistory()

    fun resetStarterRules() = repository.resetStarterRules()

    fun restoreNotification(record: NotificationRecord) {
        executor.restoreNotification(record)
    }

    fun deliverAllBatchesNow() {
        executor.deliverAllBatchesNow()
    }

    fun exportRulesJson(): String = repository.exportRulesJson()

    fun importRulesJson(rawJson: String) {
        repository.importRulesJson(rawJson) { count ->
            _backupStatus.value = if (count > 0) "Imported $count rule(s) successfully." else "No valid rules found in file."
        }
    }

    fun setBackupStatus(message: String) {
        _backupStatus.value = message
    }

    fun draftRuleFromRecord(record: NotificationRecord): NotificationRule {
        return NotificationRule(
            id = "",
            name = "Rule for ${record.appName}",
            enabled = true,
            packageNames = if (record.packageName.isNotBlank()) listOf(record.packageName) else emptyList(),
            appLabels = if (record.appName.isNotBlank()) listOf(record.appName) else emptyList(),
            contactFilter = record.title.take(40),
            matchMode = MatchMode.ANY_KEYWORD,
            actions = listOf(RuleAction(ActionType.COOLDOWN, durationSeconds = 60)),
        )
    }

    fun simulateNotification(appName: String, packageName: String, title: String, text: String) {
        val fake = IncomingNotification(
            key = "sim-${System.currentTimeMillis()}",
            packageName = packageName.ifBlank { "com.example.simulated" },
            appName = appName.ifBlank { "Simulated App" },
            title = title.ifBlank { "Test Contact" },
            text = text,
        )
        val result = repository.evaluateNotification(fake)
        _lastSimulation.value = result
        repository.recordNotification(
            NotificationRecord(
                notificationKey = fake.key,
                packageName = fake.packageName,
                appName = fake.appName,
                title = fake.title,
                text = fake.text,
                timestamp = fake.timestamp,
                matchedRuleId = result.matchedRule?.id,
                matchedRuleName = result.matchedRule?.name,
                actionSummary = "[Simulated] ${result.actionSummary}",
                category = result.category,
            )
        )
    }
}
