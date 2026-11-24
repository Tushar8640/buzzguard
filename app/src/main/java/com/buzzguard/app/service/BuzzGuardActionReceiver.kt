package com.buzzguard.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.buzzguard.app.BuzzGuardApp
import com.buzzguard.app.model.NotificationRecord

class BuzzGuardActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? BuzzGuardApp ?: return
        when (intent.action) {
            ACTION_STOP_ALARM -> {
                app.actionExecutor.stopAlarm()
            }
            ACTION_UNDO_SWIPE -> {
                val appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                if (notifId > 0) app.actionExecutor.cancelLocalNotification(notifId)
                app.actionExecutor.restoreNotification(
                    NotificationRecord(
                        notificationKey = "undo",
                        packageName = "",
                        appName = appName.ifBlank { "App" },
                        title = title,
                        text = text,
                        timestamp = System.currentTimeMillis(),
                    )
                )
            }
            ACTION_DELIVER_BATCHES -> {
                app.actionExecutor.deliverAllBatchesNow()
            }
        }
    }

    companion object {
        const val ACTION_STOP_ALARM = "com.buzzguard.app.action.STOP_ALARM"
        const val ACTION_UNDO_SWIPE = "com.buzzguard.app.action.UNDO_SWIPE"
        const val ACTION_DELIVER_BATCHES = "com.buzzguard.app.action.DELIVER_BATCHES"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }
}
