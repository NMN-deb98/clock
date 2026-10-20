package com.example.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

object SnoozeHelper {

    fun snooze(
        context: Context,
        alarmId: Int,
        minutes: Int,
        label: String,
        ringtoneUri: String,
        vibrate: Boolean
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = Calendar.getInstance().apply {
            add(Calendar.MINUTE, minutes)
        }.timeInMillis

        val intent = Intent(context, SnoozeReceiver::class.java).apply {
            putExtra("alarm_id", alarmId)
            putExtra("label", label)
            putExtra("ringtone_uri", ringtoneUri)
            putExtra("vibrate", vibrate)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            10000 + alarmId, // شناسه جدا برای هشدار به‌تعویق‌افتاده تا با اصلی تداخل نکند
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
            pendingIntent
        )
    }
}

/**
 * وقتی زمان تعویق تمام شود، دوباره صفحه هشدار را با همان تنظیمات (در صورت وجود در دیتابیس) نشان می‌دهد.
 */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("alarm_id", -1)
        if (alarmId == -1) return

        val ringIntent = Intent(context, AlarmRingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("alarm_id", alarmId)
            putExtra("label", intent.getStringExtra("label") ?: "")
            putExtra("ringtone_uri", intent.getStringExtra("ringtone_uri") ?: "")
            putExtra("vibrate", intent.getBooleanExtra("vibrate", true))
        }
        context.startActivity(ringIntent)
    }
}
