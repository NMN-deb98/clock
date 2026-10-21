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
                    NotificationHelper.showAlarmNotification(context, alarm)
                    // NotificationHelper attaches a full-screen intent. Android can then
                    // show the alarm over the lock screen when the user has allowed it.
                    if (!alarm.deleteAfterRinging) {
                        // اگر قرار نیست حذف شود، زمان بعدی را از همین حالا تنظیم کن.
                        // هشدار تکرارشونده طبق روزهای انتخاب‌شده؛ یک‌باره در روز بعد تکرار می‌شود.
                        AlarmScheduler.schedule(context, alarm)
                    }
                    // حذف اختیاری بعد از خاموش‌کردن کاربر، داخل AlarmRingActivity انجام می‌شود.
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}