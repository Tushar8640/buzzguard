package com.buzzguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.buzzguard.app.R
import com.buzzguard.app.model.IncomingNotification
import com.buzzguard.app.model.NotificationRecord
import com.buzzguard.app.model.RuleAction
import com.buzzguard.app.model.VibrationPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Executes BuzzKill-style notification actions locally on-device:
 * Custom Alert vibration/sound, Alarm ringtone with Stop button, Secret redaction,
 * Snooze/Batch digest delivery (+ Deliver All Now), Remind Me repeat pings,
 * Auto-Reply via RemoteInput, and Undo swipe prompt + restoration.
 */
class ActionExecutor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notifIdGen = AtomicInteger(1000)

    private val activeReminders = ConcurrentHashMap<String, Job>()
    private val snoozedBatches = ConcurrentHashMap<String, CopyOnWriteArrayList<IncomingNotification>>()
    private val batchRuleNames = ConcurrentHashMap<String, String>()
    private val batchJobs = ConcurrentHashMap<String, Job>()

    private var activeAlarmRingtone: Ringtone? = null
    private var activeAlarmNotifId: Int = -1

    private val _pendingBatchCount = MutableStateFlow(0)
    val pendingBatchCount: StateFlow<Int> = _pendingBatchCount.asStateFlow()

    init {
        createChannels()
    }

    private fun createChannels() {
        val channels = listOf(
            NotificationChannel(CHANNEL_ALERTS, "Custom Alerts & Restored", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_BATCH, "Snoozed Batches", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_REMINDERS, "Remind Me Alerts", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_UNDO, "Undo Dismiss Prompts", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_SECRET, "Secret Notifications", NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CHANNEL_ALARM, "Emergency Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            },
        )
        channels.forEach { nm.createNotificationChannel(it) }
    }

    fun vibrate(preset: VibrationPreset) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(preset.timings, -1))
    }

    fun triggerCustomAlert(notification: IncomingNotification, action: RuleAction) {
        vibrate(action.vibrationPreset)
        if (action.soundEnabled) {
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(context, uri)?.play()
            }
        }
        postLocalNotification(
            channelId = CHANNEL_ALERTS,
            title = "${notification.appName}: ${notification.title}",
            text = notification.text,
        )
    }

    fun triggerAlarm(notification: IncomingNotification) {
        stopAlarm()
        vibrate(VibrationPreset.SOS)
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }
            activeAlarmRingtone = ringtone
            ringtone?.play()
            // Safety auto-stop after 45s if user doesn't tap Stop
            scope.launch {
                delay(45_000)
                if (activeAlarmRingtone === ringtone) stopAlarm()
            }
        }

        val stopIntent = Intent(context, BuzzGuardActionReceiver::class.java).apply {
            action = BuzzGuardActionReceiver.ACTION_STOP_ALARM
        }
        val stopPending = PendingIntent.getBroadcast(
            context,
            9001,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val id = notifIdGen.incrementAndGet()
        activeAlarmNotifId = id
        runCatching {
            val builder = NotificationCompat.Builder(context, CHANNEL_ALARM)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("🚨 Alarm: ${notification.appName}")
                .setContentText("${notification.title} — ${notification.text}")
                .setStyle(NotificationCompat.BigTextStyle().bigText("${notification.title}\n${notification.text}"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .addAction(0, "Stop Alarm", stopPending)
                .setDeleteIntent(stopPending)
                .setAutoCancel(true)
            nm.notify(id, builder.build())
        }
    }

    fun stopAlarm() {
        runCatching { activeAlarmRingtone?.stop() }
        activeAlarmRingtone = null
        if (activeAlarmNotifId > 0) {
            nm.cancel(activeAlarmNotifId)
            activeAlarmNotifId = -1
        }
    }

    fun postSecretRedacted(notification: IncomingNotification, placeholder: String) {
        postLocalNotification(
            channelId = CHANNEL_SECRET,
            title = notification.appName,
            text = placeholder,
            priority = NotificationCompat.PRIORITY_LOW,
        )
    }

    fun enqueueSnoozeBatch(ruleId: String, ruleName: String, notification: IncomingNotification, intervalMinutes: Int) {
        batchRuleNames[ruleId] = ruleName
        val bucket = snoozedBatches.computeIfAbsent(ruleId) { CopyOnWriteArrayList() }
        bucket.add(notification)
        recomputeBatchCount()

        if (batchJobs[ruleId]?.isActive != true) {
            batchJobs[ruleId] = scope.launch {
                delay(intervalMinutes.coerceAtLeast(1) * 60_000L)
                deliverBatch(ruleId)
            }
        }
    }

    fun deliverAllBatchesNow() {
        val ruleIds = snoozedBatches.keys.toList()
        ruleIds.forEach { ruleId ->
            batchJobs.remove(ruleId)?.cancel()
            deliverBatch(ruleId)
        }
        recomputeBatchCount()
    }

    private fun deliverBatch(ruleId: String) {
        val items = snoozedBatches.remove(ruleId)?.toList().orEmpty()
        recomputeBatchCount()
        if (items.isEmpty()) return
        val ruleName = batchRuleNames[ruleId] ?: "Batch"
        val summary = items.take(6).joinToString("\n") { "• ${it.appName}: ${it.title}" }
        val extra = if (items.size > 6) "\n+${items.size - 6} more" else ""
        postLocalNotification(
            channelId = CHANNEL_BATCH,
            title = "Batch ($ruleName): ${items.size} notification${if (items.size == 1) "" else "s"}",
            text = summary + extra,
        )
    }

    private fun recomputeBatchCount() {
        _pendingBatchCount.value = snoozedBatches.values.sumOf { it.size }
    }

    fun scheduleReminder(notification: IncomingNotification, intervalMinutes: Int, maxRepeats: Int) {
        activeReminders[notification.key]?.cancel()
        activeReminders[notification.key] = scope.launch {
            repeat(maxRepeats.coerceIn(1, 10)) { attempt ->
                delay(intervalMinutes.coerceAtLeast(1) * 60_000L)
                vibrate(VibrationPreset.DOUBLE_TAP)
                postLocalNotification(
                    channelId = CHANNEL_REMINDERS,
                    title = "Reminder (${attempt + 1}/$maxRepeats): ${notification.appName}",
                    text = "${notification.title} — ${notification.text}",
                )
            }
            activeReminders.remove(notification.key)
        }
    }

    fun cancelReminder(notificationKey: String) {
        activeReminders.remove(notificationKey)?.cancel()
    }

    fun scheduleAutoReply(rawNotification: Notification?, replyText: String, delaySeconds: Int) {
        if (rawNotification == null || replyText.isBlank()) return
        val replyAction = rawNotification.actions?.firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true
        } ?: return

        scope.launch {
            delay(delaySeconds.coerceAtLeast(0) * 1000L)
            runCatching {
                val remoteInput = replyAction.remoteInputs.first()
                val intent = Intent()
                val bundle = Bundle().apply {
                    putCharSequence(remoteInput.resultKey, replyText)
                }
                RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
                replyAction.actionIntent.send(context, 0, intent)
            }
        }
    }

    fun showUndoSwipePrompt(notification: IncomingNotification) {
        val id = notifIdGen.incrementAndGet()
        val undoIntent = Intent(context, BuzzGuardActionReceiver::class.java).apply {
            action = BuzzGuardActionReceiver.ACTION_UNDO_SWIPE
            putExtra(BuzzGuardActionReceiver.EXTRA_APP_NAME, notification.appName)
            putExtra(BuzzGuardActionReceiver.EXTRA_TITLE, notification.title)
            putExtra(BuzzGuardActionReceiver.EXTRA_TEXT, notification.text)
            putExtra(BuzzGuardActionReceiver.EXTRA_NOTIF_ID, id)
        }
        val undoPending = PendingIntent.getBroadcast(
            context,
            id,
            undoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            val builder = NotificationCompat.Builder(context, CHANNEL_UNDO)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Dismissed ${notification.appName}")
                .setContentText(notification.title)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setTimeoutAfter(6_000)
                .addAction(0, "Undo", undoPending)
                .setAutoCancel(true)
            nm.notify(id, builder.build())
        }
    }

    fun cancelLocalNotification(id: Int) {
        nm.cancel(id)
    }

    fun restoreNotification(record: NotificationRecord) {
        postLocalNotification(
            channelId = CHANNEL_ALERTS,
            title = "Restored · ${record.appName}: ${record.title}",
            text = record.text,
        )
    }

    private fun postLocalNotification(
        channelId: String,
        title: String,
        text: String,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    ) {
        runCatching {
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(priority)
                .setAutoCancel(true)
            nm.notify(notifIdGen.incrementAndGet(), builder.build())
        }
    }

    companion object {
        const val CHANNEL_ALERTS = "buzzguard_alerts"
        const val CHANNEL_BATCH = "buzzguard_batch"
        const val CHANNEL_REMINDERS = "buzzguard_reminders"
        const val CHANNEL_UNDO = "buzzguard_undo"
        const val CHANNEL_SECRET = "buzzguard_secret"
        const val CHANNEL_ALARM = "buzzguard_alarm"
    }
}
