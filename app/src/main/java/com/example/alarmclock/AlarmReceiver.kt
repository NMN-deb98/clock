package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("alarm_id", -1)
        if (alarmId < 0) return
        // A fresh scheduled alarm starts a new snooze allowance; snooze broadcasts use SnoozeReceiver.
        context.getSharedPreferences("alarm_preferences", Context.MODE_PRIVATE)
            .edit().putInt("snooze_used_" + alarmId, 0).apply()

        // Start the ringing foreground service directly from the alarm broadcast.
        // Do not make the alarm's audible start wait for a Room database read.
        val hasSnapshot = intent.hasExtra("alarm_enabled_snapshot")
        val preferences = context.getSharedPreferences("alarm_preferences", Context.MODE_PRIVATE)
        val skipForHoliday = preferences.getBoolean("disable_holidays", false) &&
            PersianUtils.isIranianFixedHoliday()
        if (hasSnapshot && intent.getBooleanExtra("alarm_enabled_snapshot", false) && !skipForHoliday) {
            startRingingService(context, intent, alarmId)
        }

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = AlarmDatabase.getInstance(context).alarmDao().getById(alarmId)
                if (alarm != null && alarm.enabled) {
                    // Older PendingIntents may not contain the snapshot extras.
                    if (!hasSnapshot && !skipForHoliday) {
                        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
                            putExtra("alarm_id", alarm.id)
                            putExtra("label", alarm.label)
                            putExtra("ringtone_uri", alarm.ringtoneUri)
                            putExtra("vibrate", alarm.vibrate)
                            putExtra("wake_mode", alarm.wakeMode)
                            putExtra("math_difficulty", alarm.mathDifficulty)
                            putExtra("delete_after_ringing", alarm.deleteAfterRinging)
                            putExtra("walk_target", alarm.walkTarget)
                            putExtra("shake_difficulty", alarm.shakeDifficulty)
                            putExtra("type_mode", alarm.typeMode)
                            putExtra("custom_phrase", alarm.customPhrase)
                            putExtra("touch_target", alarm.touchTarget)
                            putExtra("pattern_size", alarm.patternSize)
                        }
                        startService(context, serviceIntent)
                    }
                    if (!alarm.deleteAfterRinging) AlarmScheduler.schedule(context, alarm)
                }
            } finally {
                result.finish()
            }
        }
    }

    private fun startRingingService(context: Context, source: Intent, alarmId: Int) {
        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            putExtra("alarm_id", alarmId)
            putExtra("label", source.getStringExtra("label").orEmpty())
            putExtra("ringtone_uri", source.getStringExtra("ringtone_uri").orEmpty())
            putExtra("vibrate", source.getBooleanExtra("vibrate", true))
            putExtra("wake_mode", source.getStringExtra("wake_mode") ?: "NONE")
            putExtra("math_difficulty", source.getStringExtra("math_difficulty") ?: "MEDIUM")
            putExtra("delete_after_ringing", source.getBooleanExtra("delete_after_ringing", false))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun startService(context: Context, serviceIntent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
