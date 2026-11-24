package com.buzzguard.app

import com.buzzguard.app.engine.RuleEngine
import com.buzzguard.app.model.ActionType
import com.buzzguard.app.model.DeviceContext
import com.buzzguard.app.model.IncomingNotification
import com.buzzguard.app.model.MatchMode
import com.buzzguard.app.model.NotificationRule
import com.buzzguard.app.model.RecordCategory
import com.buzzguard.app.model.RingerFilter
import com.buzzguard.app.model.RuleAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RuleEngineTest {

    @Test
    fun cooldownAllowsFirstAndSuppressesBurst() {
        var now = 1_000_000L
        val engine = RuleEngine(clock = { now })
        val rule = NotificationRule(
            id = "r1",
            name = "Chat Cooldown",
            packageNames = listOf("com.whatsapp"),
            actions = listOf(RuleAction(ActionType.COOLDOWN, durationSeconds = 30)),
        )
        val notif = IncomingNotification(
            key = "k1",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Team Group",
            text = "hello 1",
        )

        val first = engine.evaluate(notif, listOf(rule))
        assertFalse(first.suppressedByCooldown)

        now += 10_000L
        val second = engine.evaluate(notif.copy(key = "k2", text = "hello 2"), listOf(rule))
        assertTrue(second.suppressedByCooldown)
        assertEquals(RecordCategory.COOLDOWN, second.category)

        now += 25_000L // 35s after first -> cooldown expired
        val third = engine.evaluate(notif.copy(key = "k3", text = "hello 3"), listOf(rule))
        assertFalse(third.suppressedByCooldown)
    }

    @Test
    fun conflictResolutionPrefersMoreSpecificRule() {
        val engine = RuleEngine()
        val generalMute = NotificationRule(
            id = "general",
            name = "Mute WhatsApp",
            packageNames = listOf("com.whatsapp"),
            actions = listOf(RuleAction(ActionType.DISMISS)),
        )
        val urgentAlarm = NotificationRule(
            id = "urgent",
            name = "Urgent Alarm",
            packageNames = listOf("com.whatsapp"),
            keywords = listOf("urgent"),
            actions = listOf(RuleAction(ActionType.ALARM)),
        )
        val notif = IncomingNotification(
            key = "k1",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Boss",
            text = "URGENT server down!",
        )

        val result = engine.evaluate(notif, listOf(generalMute, urgentAlarm))
        assertNotNull(result.matchedRule)
        assertEquals("urgent", result.matchedRule?.id)
        assertEquals(RecordCategory.ALERTED, result.category)
    }

    @Test
    fun allKeywordsAndExcludeKeywordsWorkTogether() {
        val engine = RuleEngine()
        val rule = NotificationRule(
            id = "r-all",
            name = "Security Camera Only",
            matchMode = MatchMode.ALL_KEYWORDS,
            keywords = listOf("front", "motion"),
            excludeKeywords = listOf("cat"),
            actions = listOf(RuleAction(ActionType.ALARM)),
        )

        val matchNotif = IncomingNotification("1", "com.cam", "Cam", "Front Door", "Motion detected")
        val excludedNotif = IncomingNotification("2", "com.cam", "Cam", "Front Door", "Motion detected: cat")
        val partialNotif = IncomingNotification("3", "com.cam", "Cam", "Backyard", "Motion detected")

        assertTrue(engine.matches(matchNotif, rule))
        assertFalse(engine.matches(excludedNotif, rule))
        assertFalse(engine.matches(partialNotif, rule))
    }

    @Test
    fun deviceContextFiltersScreenWifiAndRinger() {
        val engine = RuleEngine()
        val rule = NotificationRule(
            id = "ctx",
            name = "Night Wifi Silent Alarm",
            onlyWhenScreenOff = true,
            onlyOnWifi = true,
            ringerFilter = RingerFilter.VIBRATE_OR_SILENT,
            actions = listOf(RuleAction(ActionType.ALARM)),
        )
        val notif = IncomingNotification("1", "com.cam", "Cam", "Alert", "Motion")

        val matchingCtx = DeviceContext(isScreenInteractive = false, isSilentOrVibrate = true, isOnWifi = true)
        val screenOnCtx = DeviceContext(isScreenInteractive = true, isSilentOrVibrate = true, isOnWifi = true)
        val noWifiCtx = DeviceContext(isScreenInteractive = false, isSilentOrVibrate = true, isOnWifi = false)
        val normalRingerCtx = DeviceContext(isScreenInteractive = false, isSilentOrVibrate = false, isOnWifi = true)

        assertTrue(engine.matches(notif, rule, deviceContext = matchingCtx))
        assertFalse(engine.matches(notif, rule, deviceContext = screenOnCtx))
        assertFalse(engine.matches(notif, rule, deviceContext = noWifiCtx))
        assertFalse(engine.matches(notif, rule, deviceContext = normalRingerCtx))
    }

    @Test
    fun scheduleActiveDaysFilterWorks() {
        val monday10am = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 23, 10, 0, 0) // Monday
        }.timeInMillis
        val sunday10am = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 22, 10, 0, 0) // Sunday
        }.timeInMillis

        val engine = RuleEngine()
        val weekdayRule = NotificationRule(
            id = "wd",
            name = "Weekday Work Hours",
            scheduleEnabled = true,
            startHour = 9,
            endHour = 17,
            activeDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
            actions = listOf(RuleAction(ActionType.SNOOZE_BATCH, intervalMinutes = 30)),
        )
        val notif = IncomingNotification("1", "com.slack", "Slack", "Channel", "Update")

        assertTrue(engine.matches(notif, weekdayRule, nowMillis = monday10am))
        assertFalse(engine.matches(notif, weekdayRule, nowMillis = sunday10am))
    }
}
