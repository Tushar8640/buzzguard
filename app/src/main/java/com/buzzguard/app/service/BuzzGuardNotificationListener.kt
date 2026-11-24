package com.buzzguard.app.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.buzzguard.app.BuzzGuardApp
import com.buzzguard.app.model.ActionType
import com.buzzguard.app.model.DeviceContext
import com.buzzguard.app.model.IncomingNotification
import com.buzzguard.app.model.NotificationRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BuzzGuardNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        // Never intercept our own notifications (prevents infinite loops)
        if (sbn.packageName == packageName) return
        if (sbn.isOngoing) return

        val app = application as? BuzzGuardApp ?: return
        val incoming = sbn.toIncomingNotification(this)
        val deviceContext = readDeviceContext()
        val evaluation = app.repository.evaluateNotification(incoming, deviceContext)

        app.repository.recordNotification(
            NotificationRecord(
                notificationKey = incoming.key,
                packageName = incoming.packageName,
                appName = incoming.appName,
                title = incoming.title,
                text = incoming.text,
                timestamp = incoming.timestamp,
                matchedRuleId = evaluation.matchedRule?.id,
                matchedRuleName = evaluation.matchedRule?.name,
                actionSummary = evaluation.actionSummary,
                category = evaluation.category,
            )
        )

        val rule = evaluation.matchedRule ?: return
        for (action in evaluation.actionsToExecute) {
            when (action.type) {
                ActionType.COOLDOWN -> {
                    if (evaluation.suppressedByCooldown) {
                        runCatching { cancelNotification(sbn.key) }
                    }
                }
                ActionType.DISMISS -> {
                    if (action.durationSeconds <= 0) {
                        runCatching { cancelNotification(sbn.key) }
                    } else {
                        scope.launch {
                            delay(action.durationSeconds * 1000L)
                            runCatching { cancelNotification(sbn.key) }
                        }
                    }
                }
                ActionType.SNOOZE_BATCH -> {
                    runCatching { cancelNotification(sbn.key) }
                    app.actionExecutor.enqueueSnoozeBatch(
                        ruleId = rule.id,
                        ruleName = rule.name,
                        notification = incoming,
                        intervalMinutes = action.intervalMinutes,
                    )
                }
                ActionType.CUSTOM_ALERT -> {
                    app.actionExecutor.triggerCustomAlert(incoming, action)
                }
                ActionType.REMIND_ME -> {
                    app.actionExecutor.scheduleReminder(
                        notification = incoming,
                        intervalMinutes = action.intervalMinutes,
                        maxRepeats = action.maxRepeats,
                    )
                }
                ActionType.ALARM -> {
                    app.actionExecutor.triggerAlarm(incoming)
                }
                ActionType.AUTO_REPLY -> {
                    app.actionExecutor.scheduleAutoReply(
                        rawNotification = sbn.notification,
                        replyText = action.replyText,
                        delaySeconds = action.durationSeconds,
                    )
                }
                ActionType.SECRET -> {
                    runCatching { cancelNotification(sbn.key) }
                    app.actionExecutor.postSecretRedacted(incoming, action.placeholderText)
                }
            }
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int,
    ) {
        if (sbn == null || sbn.packageName == packageName) return
        val app = application as? BuzzGuardApp ?: return

        // Cancel any active Remind Me job once the notification is opened or cleared
        app.actionExecutor.cancelReminder(sbn.key)

        // Capture accidental manual swipes for the Undo feature
        if (reason == REASON_CANCEL) {
            val swiped = sbn.toIncomingNotification(this)
            app.repository.recordUserSwiped(swiped)
            app.actionExecutor.showUndoSwipePrompt(swiped)
        }
    }

    private fun readDeviceContext(): DeviceContext {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val isInteractive = powerManager?.isInteractive ?: true
        val ringer = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        val isSilentOrVibrate = ringer == AudioManager.RINGER_MODE_SILENT || ringer == AudioManager.RINGER_MODE_VIBRATE
        val isOnWifi = runCatching {
            val active = connectivity?.activeNetwork
            val caps = connectivity?.getNetworkCapabilities(active)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }.getOrDefault(false)

        return DeviceContext(
            isScreenInteractive = isInteractive,
            isSilentOrVibrate = isSilentOrVibrate,
            isOnWifi = isOnWifi,
        )
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?: return false
            val expected = ComponentName(context, BuzzGuardNotificationListener::class.java).flattenToString()
            return flat.split(":").any { it.equals(expected, ignoreCase = true) }
        }

        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

private fun StatusBarNotification.toIncomingNotification(context: Context): IncomingNotification {
    val extras = notification.extras
    val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val text = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        ?: extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
    val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
    val appName = runCatching {
        val ai = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(ai).toString()
    }.getOrDefault(packageName)

    return IncomingNotification(
        key = key,
        packageName = packageName,
        appName = appName,
        title = title.ifBlank { appName },
        text = text,
        subText = subText,
        timestamp = postTime,
    )
}
