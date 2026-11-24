package com.buzzguard.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.buzzguard.app.model.ActionType
import com.buzzguard.app.model.MatchMode
import com.buzzguard.app.model.NotificationRecord
import com.buzzguard.app.model.NotificationRule
import com.buzzguard.app.model.RecordCategory
import com.buzzguard.app.model.RingerFilter
import com.buzzguard.app.model.RuleAction
import com.buzzguard.app.model.VibrationPreset
import org.json.JSONArray
import org.json.JSONObject

class BuzzGuardDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_RULES (
                id TEXT PRIMARY KEY,
                priority INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                payload TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_HISTORY (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                notif_key TEXT NOT NULL,
                package_name TEXT NOT NULL,
                app_name TEXT NOT NULL,
                title TEXT NOT NULL,
                text TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                matched_rule_id TEXT,
                matched_rule_name TEXT,
                action_summary TEXT NOT NULL,
                category TEXT NOT NULL,
                dismissed_by_user INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_history_ts ON $TABLE_HISTORY(timestamp DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RULES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    fun getAllRules(): List<NotificationRule> {
        val list = mutableListOf<NotificationRule>()
        readableDatabase.query(
            TABLE_RULES,
            arrayOf("payload"),
            null,
            null,
            null,
            null,
            "priority DESC, created_at ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching { decodeRule(JSONObject(cursor.getString(0))) }
                    .getOrNull()
                    ?.let(list::add)
            }
        }
        return list
    }

    fun upsertRule(rule: NotificationRule) {
        val cv = ContentValues().apply {
            put("id", rule.id)
            put("priority", rule.priority)
            put("enabled", if (rule.enabled) 1 else 0)
            put("created_at", rule.createdAt)
            put("payload", encodeRule(rule).toString())
        }
        writableDatabase.insertWithOnConflict(TABLE_RULES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteRule(ruleId: String) {
        writableDatabase.delete(TABLE_RULES, "id = ?", arrayOf(ruleId))
    }

    fun insertHistory(record: NotificationRecord): Long {
        val cv = ContentValues().apply {
            put("notif_key", record.notificationKey)
            put("package_name", record.packageName)
            put("app_name", record.appName)
            put("title", record.title)
            put("text", record.text)
            put("timestamp", record.timestamp)
            put("matched_rule_id", record.matchedRuleId)
            put("matched_rule_name", record.matchedRuleName)
            put("action_summary", record.actionSummary)
            put("category", record.category.name)
            put("dismissed_by_user", if (record.dismissedByUser) 1 else 0)
        }
        val id = writableDatabase.insert(TABLE_HISTORY, null, cv)
        // Keep history bounded to latest 500 records locally
        writableDatabase.execSQL(
            """
            DELETE FROM $TABLE_HISTORY WHERE id NOT IN (
                SELECT id FROM $TABLE_HISTORY ORDER BY timestamp DESC LIMIT $MAX_HISTORY_ROWS
            )
            """.trimIndent()
        )
        return id
    }

    fun getHistory(limit: Int = 200): List<NotificationRecord> {
        val list = mutableListOf<NotificationRecord>()
        readableDatabase.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "timestamp DESC",
            limit.toString(),
        ).use { c ->
            while (c.moveToNext()) {
                val categoryName = c.getString(c.getColumnIndexOrThrow("category"))
                val category = runCatching { RecordCategory.valueOf(categoryName) }
                    .getOrDefault(RecordCategory.DELIVERED)
                list += NotificationRecord(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    notificationKey = c.getString(c.getColumnIndexOrThrow("notif_key")),
                    packageName = c.getString(c.getColumnIndexOrThrow("package_name")),
                    appName = c.getString(c.getColumnIndexOrThrow("app_name")),
                    title = c.getString(c.getColumnIndexOrThrow("title")),
                    text = c.getString(c.getColumnIndexOrThrow("text")),
                    timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                    matchedRuleId = c.getString(c.getColumnIndexOrThrow("matched_rule_id")),
                    matchedRuleName = c.getString(c.getColumnIndexOrThrow("matched_rule_name")),
                    actionSummary = c.getString(c.getColumnIndexOrThrow("action_summary")),
                    category = category,
                    dismissedByUser = c.getInt(c.getColumnIndexOrThrow("dismissed_by_user")) == 1,
                )
            }
        }
        return list
    }

    fun clearHistory() {
        writableDatabase.delete(TABLE_HISTORY, null, null)
    }

    fun exportRulesJson(): String {
        val array = JSONArray()
        getAllRules().forEach { array.put(encodeRule(it)) }
        return JSONObject().apply {
            put("app", "BuzzGuard")
            put("version", 1)
            put("rules", array)
        }.toString(2)
    }

    fun importRulesJson(rawJson: String): Int {
        val root = JSONObject(rawJson)
        val array = root.optJSONArray("rules") ?: return 0
        var count = 0
        for (i in 0 until array.length()) {
            val rule = runCatching { decodeRule(array.getJSONObject(i)) }.getOrNull() ?: continue
            upsertRule(rule)
            count++
        }
        return count
    }

    private fun encodeRule(rule: NotificationRule): JSONObject = JSONObject().apply {
        put("id", rule.id)
        put("name", rule.name)
        put("enabled", rule.enabled)
        put("priority", rule.priority)
        put("packageNames", JSONArray(rule.packageNames))
        put("appLabels", JSONArray(rule.appLabels))
        put("matchMode", rule.matchMode.name)
        put("keywords", JSONArray(rule.keywords))
        put("excludeKeywords", JSONArray(rule.excludeKeywords))
        put("contactFilter", rule.contactFilter)
        put("scheduleEnabled", rule.scheduleEnabled)
        put("startHour", rule.startHour)
        put("startMinute", rule.startMinute)
        put("endHour", rule.endHour)
        put("endMinute", rule.endMinute)
        put("activeDays", JSONArray(rule.activeDays.toList()))
        put("onlyWhenScreenOff", rule.onlyWhenScreenOff)
        put("onlyOnWifi", rule.onlyOnWifi)
        put("ringerFilter", rule.ringerFilter.name)
        put("createdAt", rule.createdAt)
        put(
            "actions",
            JSONArray().apply {
                rule.actions.forEach { a ->
                    put(
                        JSONObject().apply {
                            put("type", a.type.name)
                            put("durationSeconds", a.durationSeconds)
                            put("intervalMinutes", a.intervalMinutes)
                            put("maxRepeats", a.maxRepeats)
                            put("vibrationPreset", a.vibrationPreset.name)
                            put("soundEnabled", a.soundEnabled)
                            put("replyText", a.replyText)
                            put("placeholderText", a.placeholderText)
                        }
                    )
                }
            }
        )
    }

    private fun decodeRule(obj: JSONObject): NotificationRule {
        fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return List(length()) { idx -> optString(idx) }.filter { it.isNotBlank() }
        }

        val actionsJson = obj.optJSONArray("actions")
        val actions = if (actionsJson != null) {
            List(actionsJson.length()) { i ->
                val a = actionsJson.getJSONObject(i)
                RuleAction(
                    type = runCatching { ActionType.valueOf(a.optString("type")) }.getOrDefault(ActionType.COOLDOWN),
                    durationSeconds = a.optInt("durationSeconds", 60),
                    intervalMinutes = a.optInt("intervalMinutes", 15),
                    maxRepeats = a.optInt("maxRepeats", 3),
                    vibrationPreset = runCatching { VibrationPreset.valueOf(a.optString("vibrationPreset")) }
                        .getOrDefault(VibrationPreset.DOUBLE_TAP),
                    soundEnabled = a.optBoolean("soundEnabled", true),
                    replyText = a.optString("replyText", "I'm busy right now, will reply soon."),
                    placeholderText = a.optString("placeholderText", "New private notification"),
                )
            }
        } else {
            emptyList()
        }

        val daysJson = obj.optJSONArray("activeDays")
        val activeDays = if (daysJson != null) {
            (0 until daysJson.length()).map { daysJson.optInt(it) }.filter { it in 1..7 }.toSet()
        } else {
            emptySet()
        }

        return NotificationRule(
            id = obj.getString("id"),
            name = obj.optString("name", "Untitled rule"),
            enabled = obj.optBoolean("enabled", true),
            priority = obj.optInt("priority", 0),
            packageNames = obj.optJSONArray("packageNames").toStringList(),
            appLabels = obj.optJSONArray("appLabels").toStringList(),
            matchMode = runCatching { MatchMode.valueOf(obj.optString("matchMode")) }
                .getOrDefault(MatchMode.ANY_KEYWORD),
            keywords = obj.optJSONArray("keywords").toStringList(),
            excludeKeywords = obj.optJSONArray("excludeKeywords").toStringList(),
            contactFilter = obj.optString("contactFilter", ""),
            scheduleEnabled = obj.optBoolean("scheduleEnabled", false),
            startHour = obj.optInt("startHour", 9),
            startMinute = obj.optInt("startMinute", 0),
            endHour = obj.optInt("endHour", 18),
            endMinute = obj.optInt("endMinute", 0),
            activeDays = activeDays,
            onlyWhenScreenOff = obj.optBoolean("onlyWhenScreenOff", false),
            onlyOnWifi = obj.optBoolean("onlyOnWifi", false),
            ringerFilter = runCatching { RingerFilter.valueOf(obj.optString("ringerFilter")) }
                .getOrDefault(RingerFilter.ANY),
            actions = actions,
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
        )
    }

    companion object {
        private const val DB_NAME = "buzzguard.db"
        private const val DB_VERSION = 1
        private const val TABLE_RULES = "rules"
        private const val TABLE_HISTORY = "notification_history"
        private const val MAX_HISTORY_ROWS = 500
    }
}
