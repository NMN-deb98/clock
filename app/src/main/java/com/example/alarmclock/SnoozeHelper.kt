package com.example.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object SnoozeHelper {

    fun snooze(
        context: Context,
        alarmId: Int,
        minutes: Int,
        label: String,
        ringtoneUri: String,
        vibrate: Boolean,
        wakeMode: String = "NONE",
        mathDifficulty: String = "MEDIUM",
        deleteAfterRinging: Boolean = false
    ) {
        if (alarmId < 0 || minutes <= 0) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = Calendar.getInstance().apply {
            add(Calendar.MINUTE, minutes)
        }.timeInMillis

        val intent = Intent(context, SnoozeReceiver::class.java).apply {
            putExtra("alarm_id", alarmId)
            putExtra("label", label)
            putExtra("ringtone_uri", ringtoneUri)
            putExtra("vibrate", vibrate)
            putExtra("wake_mode", wakeMode)
            putExtra("math_difficulty", mathDifficulty)
            putExtra("delete_after_ringing", deleteAfterRinging)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            10000 + alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                pendingIntent
            )
        } catch (_: SecurityException) {
            // Exact-alarm access can be revoked while the app is running.
            // Avoid crashing; callers can restore exact-alarm access in Settings.
        }
    }
}

/**
 * The snooze broadcast must start the ringing service itself. Relying only on a
 * background notification/full-screen intent can silently fail on newer Android
 * versions, leaving the user with no sound after the snooze interval.
 */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("alarm_id", -1)
        if (alarmId < 0) return

        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            putExtra("alarm_id", alarmId)
            putExtra("label", intent.getStringExtra("label").orEmpty())
            putExtra("ringtone_uri", intent.getStringExtra("ringtone_uri").orEmpty())
            putExtra("vibrate", intent.getBooleanExtra("vibrate", true))
            putExtra("wake_mode", intent.getStringExtra("wake_mode") ?: "NONE")
            putExtra("math_difficulty", intent.getStringExtra("math_difficulty") ?: "MEDIUM")
            putExtra("delete_after_ringing", intent.getBooleanExtra("delete_after_ringing", false))
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (_: RuntimeException) {
            // Do not crash the receiver if Android blocks background service launch.
            // The scheduled alarm remains handled by the OS and the next app launch
            // can surface diagnostics/permissions to the user.
        }
    }
}
