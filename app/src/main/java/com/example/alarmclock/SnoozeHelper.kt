package com.example.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object SnoozeHelper {

    /** Per-alarm snooze settings are saved by the alarm edit screen. */
    fun saveConfig(context: Context, alarmId: Int, count: Int, minutes: Int) {
        context.getSharedPreferences("alarm_preferences", Context.MODE_PRIVATE).edit()
            .putInt("snooze_cfg_count_$alarmId", count.coerceIn(0, 10))
            .putInt("snooze_cfg_min_$alarmId", minutes.coerceIn(1, 30))
            .apply()
    }

    fun allowed(context: Context, alarmId: Int): Int {
        val p = context.getSharedPreferences("alarm_preferences", Context.MODE_PRIVATE)
        return p.getInt("snooze_cfg_count_$alarmId", p.getInt("snooze_count", 3)).coerceIn(0, 10)
    }

    fun minutesFor(context: Context, alarmId: Int): Int {
        val p = context.getSharedPreferences("alarm_preferences", Context.MODE_PRIVATE)
        return p.getInt("snooze_cfg_min_$alarmId", p.getInt("snooze_minutes", 10)).coerceIn(1, 30)
    }

    fun snooze(
        context: Context,
        alarmId: Int,
        minutes: Int,
        label: String,
        ringtoneUri: String,
        vibrate: Boolean,
        wakeMode: String = "NONE",
        mathDifficulty: String = "MEDIUM",
        deleteAfterRinging: Boolean = false,
        walkTarget: Int = 40,
        shakeDifficulty: String = "MEDIUM",
        typeMode: String = "RANDOM",
        customPhrase: String = "",
        touchTarget: Int = 30,
        patternSize: Int = 3
    ): Boolean {
        if (alarmId < 0 || minutes <= 0) return false
        val preferences = context.getSharedPreferences("alarm_preferences", Context.MODE_PRIVATE)
        val allowedSnoozes = allowed(context, alarmId)
        val usedSnoozes = preferences.getInt("snooze_used_" + alarmId, 0)
        if (usedSnoozes >= allowedSnoozes) {
            android.widget.Toast.makeText(context, "تعداد دفعات چرت زدن تمام شده است.", android.widget.Toast.LENGTH_LONG).show()
            return false
        }
        val selectedMinutes = minutesFor(context, alarmId)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = Calendar.getInstance().apply {
            add(Calendar.MINUTE, selectedMinutes)
        }.timeInMillis

        val intent = Intent(context, SnoozeReceiver::class.java).apply {
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
            preferences.edit().putInt("snooze_used_" + alarmId, usedSnoozes + 1).apply()
            return true
        } catch (_: SecurityException) {
            // Exact-alarm access can be revoked while the app is running.
            // Avoid crashing; callers can restore exact-alarm access in Settings.
            return false
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
            putExtra("walk_target", intent.getIntExtra("walk_target", 40))
            putExtra("shake_difficulty", intent.getStringExtra("shake_difficulty") ?: "MEDIUM")
            putExtra("type_mode", intent.getStringExtra("type_mode") ?: "RANDOM")
            putExtra("custom_phrase", intent.getStringExtra("custom_phrase").orEmpty())
            putExtra("touch_target", intent.getIntExtra("touch_target", 30))
            putExtra("pattern_size", intent.getIntExtra("pattern_size", 3))
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
