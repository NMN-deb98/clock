package com.example.alarmclock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val CHANNEL_ID = "alarm_channel_v2"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH).apply {
                        description = context.getString(R.string.alarm_notification_channel_desc)
                        setSound(null, null)
                        enableVibration(false)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                )
            }
        }
    }

    fun buildAlarmNotification(
        context: Context, alarmId: Int, label: String, ringtoneUri: String,
        vibrate: Boolean, wakeMode: String, mathDifficulty: String, deleteAfterRinging: Boolean
    ): Notification {
        createChannel(context)
        val intent = Intent(context, AlarmRingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("alarm_id", alarmId)
            putExtra("label", label)
            putExtra("ringtone_uri", ringtoneUri)
            putExtra("vibrate", vibrate)
            putExtra("wake_mode", wakeMode)
            putExtra("math_difficulty", mathDifficulty)
            putExtra("delete_after_ringing", deleteAfterRinging)
        }
        val pending = PendingIntent.getActivity(context, alarmId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = label.ifBlank { context.getString(R.string.no_note) }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.alarm_ringing))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setAutoCancel(false)
            .build()
    }

    fun showAlarmNotification(context: Context, alarm: Alarm) {
        val notification = buildAlarmNotification(context, alarm.id, alarm.label, alarm.ringtoneUri, alarm.vibrate, alarm.wakeMode, alarm.mathDifficulty, alarm.deleteAfterRinging)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(alarm.id, notification)
    }

    fun cancelNotification(context: Context, alarmId: Int) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(alarmId)
    }
}
