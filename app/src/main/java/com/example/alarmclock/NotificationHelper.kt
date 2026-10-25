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
    const val CHANNEL_ID = "alarm_channel_v3"

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
        vibrate: Boolean, wakeMode: String, mathDifficulty: String, deleteAfterRinging: Boolean,
        walkTarget: Int = 40, shakeDifficulty: String = "MEDIUM", typeMode: String = "RANDOM",
        customPhrase: String = "", touchTarget: Int = 30, patternSize: Int = 3
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
            putExtra("walk_target", walkTarget)
            putExtra("shake_difficulty", shakeDifficulty)
            putExtra("type_mode", typeMode)
            putExtra("custom_phrase", customPhrase)
            putExtra("touch_target", touchTarget)
            putExtra("pattern_size", patternSize)
        }
        val pending = PendingIntent.getActivity(context, alarmId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = label.ifBlank { context.getString(R.string.no_note) }
        fun actionIntent(action: String) = Intent(context, AlarmActionReceiver::class.java).apply {
            this.action = action
            putExtra("alarm_id", alarmId)
            putExtra("label", label)
            putExtra("ringtone_uri", ringtoneUri)
            putExtra("vibrate", vibrate)
            putExtra("wake_mode", wakeMode)
            putExtra("math_difficulty", mathDifficulty)
            putExtra("delete_after_ringing", deleteAfterRinging)
            putExtra("walk_target", walkTarget)
            putExtra("shake_difficulty", shakeDifficulty)
            putExtra("type_mode", typeMode)
            putExtra("custom_phrase", customPhrase)
            putExtra("touch_target", touchTarget)
            putExtra("pattern_size", patternSize)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context, 20000 + alarmId, actionIntent(AlarmActionReceiver.ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // With a wake-up challenge the dismiss button opens the challenge screen instead of silencing directly.
        val dismissPending = if (wakeMode == "NONE") PendingIntent.getBroadcast(
            context, 30000 + alarmId, actionIntent(AlarmActionReceiver.ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) else pending
        val dismissText = if (wakeMode == "NONE") context.getString(R.string.dismiss) else "حل چالش و خاموش کردن"
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.alarm_ringing) + " • " + PersianUtils.two(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) + ":" + PersianUtils.two(java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, dismissText, dismissPending)
            .addAction(0, PersianUtils.digits(SnoozeHelper.minutesFor(context, alarmId)) + " دقیقه بعد", snoozePending)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setAutoCancel(false)
            .build()
    }

    fun showAlarmNotification(context: Context, alarm: Alarm) {
        val notification = buildAlarmNotification(context, alarm.id, alarm.label, alarm.ringtoneUri, alarm.vibrate, alarm.wakeMode, alarm.mathDifficulty, alarm.deleteAfterRinging,
            alarm.walkTarget, alarm.shakeDifficulty, alarm.typeMode, alarm.customPhrase, alarm.touchTarget, alarm.patternSize)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(alarm.id, notification)
    }

    fun cancelNotification(context: Context, alarmId: Int) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(alarmId)
    }
}
