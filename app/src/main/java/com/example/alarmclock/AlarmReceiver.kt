package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("alarm_id", -1)
        if (alarmId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AlarmDatabase.getInstance(context).alarmDao()
                val alarm = dao.getById(alarmId)
                if (alarm != null && alarm.enabled) {

                    // نمایش نوتیفیکیشن (شامل یادداشت)
                    NotificationHelper.showAlarmNotification(context, alarm)

                    // باز کردن صفحه تمام‌صفحه هشدار
                    val ringIntent = Intent(context, AlarmRingActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                        putExtra("alarm_id", alarm.id)
                        putExtra("label", alarm.label)
                        putExtra("ringtone_uri", alarm.ringtoneUri)
                        putExtra("vibrate", alarm.vibrate)
                    }
                    context.startActivity(ringIntent)

                    if (alarm.isRepeating()) {
                        // زمان بعدی تکرار را دوباره تنظیم کن
                        AlarmScheduler.schedule(context, alarm)
                    } else {
                        // هشدار یک‌باره: بعد از زنگ خوردن حذف شود
                        dao.deleteById(alarm.id)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
