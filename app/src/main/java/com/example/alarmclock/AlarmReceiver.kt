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
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = AlarmDatabase.getInstance(context).alarmDao().getById(alarmId)
                if (alarm != null && alarm.enabled) {
                    val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
                        putExtra("alarm_id", alarm.id)
                        putExtra("label", alarm.label)
                        putExtra("ringtone_uri", alarm.ringtoneUri)
                        putExtra("vibrate", alarm.vibrate)
                        putExtra("wake_mode", alarm.wakeMode)
                        putExtra("math_difficulty", alarm.mathDifficulty)
                        putExtra("delete_after_ringing", alarm.deleteAfterRinging)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
                    else context.startService(serviceIntent)
                    if (!alarm.deleteAfterRinging) AlarmScheduler.schedule(context, alarm)
                }
            } finally {
                result.finish()
            }
        }
    }
}
