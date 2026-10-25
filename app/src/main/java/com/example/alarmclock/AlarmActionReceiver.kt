package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the "خاموش" and "۱۰ دقیقه بعد" buttons shown on the alarm notification (also on the lock screen). */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("alarm_id", -1)
        val stop = Intent(context, AlarmRingingService::class.java).apply { action = AlarmRingingService.ACTION_STOP }
        when (intent.action) {
            ACTION_SNOOZE -> {
                val prefs = context.getSharedPreferences("alarm_preferences", Context.MODE_PRIVATE)
                val maxSnoozes = SnoozeHelper.allowed(context, alarmId)
                val usedSnoozes = prefs.getInt("snooze_used_" + alarmId, 0)
                if (usedSnoozes >= maxSnoozes) {
                    android.widget.Toast.makeText(context, "تعداد دفعات چرت زدن تمام شده است.", android.widget.Toast.LENGTH_LONG).show()
                    return
                }
                stopRinging(context, stop)
                NotificationHelper.cancelNotification(context, alarmId)
                SnoozeHelper.snooze(
                    context, alarmId, minutes = 10,
                    label = intent.getStringExtra("label").orEmpty(),
                    ringtoneUri = intent.getStringExtra("ringtone_uri").orEmpty(),
                    vibrate = intent.getBooleanExtra("vibrate", true),
                    wakeMode = intent.getStringExtra("wake_mode") ?: "NONE",
                    mathDifficulty = intent.getStringExtra("math_difficulty") ?: "MEDIUM",
                    deleteAfterRinging = intent.getBooleanExtra("delete_after_ringing", false),
                    walkTarget = intent.getIntExtra("walk_target", 40),
                    shakeDifficulty = intent.getStringExtra("shake_difficulty") ?: "MEDIUM",
                    typeMode = intent.getStringExtra("type_mode") ?: "RANDOM",
                    customPhrase = intent.getStringExtra("custom_phrase").orEmpty(),
                    touchTarget = intent.getIntExtra("touch_target", 30),
                    patternSize = intent.getIntExtra("pattern_size", 3)
                )
            }
            ACTION_DISMISS -> {
                stopRinging(context, stop)
                NotificationHelper.cancelNotification(context, alarmId)
                if (intent.getBooleanExtra("delete_after_ringing", false) && alarmId != -1) {
                    val result = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            AlarmScheduler.cancel(context, Alarm(id = alarmId, hour = 0, minute = 0))
                            AlarmDatabase.getInstance(context).alarmDao().deleteById(alarmId)
                        } finally {
                            result.finish()
                        }
                    }
                }
                // Close the full-screen ring page if it is open.
                context.sendBroadcast(Intent(ACTION_CLOSE_RING).setPackage(context.packageName))
            }
        }
    }

    private fun stopRinging(context: Context, stop: Intent) {
        runCatching { context.startService(stop) }
        runCatching { context.stopService(Intent(context, AlarmRingingService::class.java)) }
    }

    companion object {
        const val ACTION_SNOOZE = "com.example.alarmclock.action.SNOOZE_FROM_NOTIFICATION"
        const val ACTION_DISMISS = "com.example.alarmclock.action.DISMISS_FROM_NOTIFICATION"
        const val ACTION_CLOSE_RING = "com.example.alarmclock.action.CLOSE_RING_SCREEN"
    }
}
