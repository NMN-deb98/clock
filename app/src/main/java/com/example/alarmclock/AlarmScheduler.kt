package com.example.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object AlarmScheduler {

    fun schedule(context: Context, alarm: Alarm) {
        DeskClockWidgetProvider.refreshAll(context, 1500L)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = nextTriggerTimeMillis(alarm)

        // Include a snapshot of the alarm in the broadcast so playback can start
        // immediately. The receiver can read Room asynchronously afterward to
        // confirm the alarm is still enabled and schedule its next occurrence.
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm_id", alarm.id)
            putExtra("alarm_enabled_snapshot", alarm.enabled)
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
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                // Do not silently replace a precise alarm with an inexact one.
                // MainActivity requests this special access from the user.
                return
            }
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                pendingIntent
            )
        } catch (_: SecurityException) {
            // Exact-alarm access may be revoked between the check and scheduling.
            // Never crash the UI; the permission flow can restore scheduling.
        }
    }

    fun cancel(context: Context, alarm: Alarm) {
        DeskClockWidgetProvider.refreshAll(context, 1500L)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * زمان بعدی وقوع هشدار را بر اساس ساعت/دقیقه و روزهای انتخاب‌شده محاسبه می‌کند.
     * اگر هیچ روزی انتخاب نشده باشد، نزدیک‌ترین زمان آینده امروز یا فردا در نظر گرفته می‌شود.
     */
    fun nextTriggerTimeMillis(alarm: Alarm): Long {
        val now = Calendar.getInstance()
        val days = alarm.dayList()

        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (days.isEmpty()) {
            if (candidate.timeInMillis <= now.timeInMillis) {
                candidate.add(Calendar.DAY_OF_YEAR, 1)
            }
            return candidate.timeInMillis
        }

        for (i in 0..7) {
            val check = candidate.clone() as Calendar
            check.add(Calendar.DAY_OF_YEAR, i)
            val dayOfWeek = check.get(Calendar.DAY_OF_WEEK)
            if (days.contains(dayOfWeek)) {
                if (i == 0 && check.timeInMillis <= now.timeInMillis) continue
                return check.timeInMillis
            }
        }
        candidate.add(Calendar.DAY_OF_YEAR, 7)
        return candidate.timeInMillis
    }
}
