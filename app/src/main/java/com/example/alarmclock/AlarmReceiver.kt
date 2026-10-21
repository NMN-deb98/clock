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
                    val ringIntent = Intent(context, AlarmRingActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("alarm_id", alarm.id)
                        putExtra("label", alarm.label)
                        putExtra("ringtone_uri", alarm.ringtoneUri)
                        putExtra("vibrate", alarm.vibrate)
                        putExtra("wake_mode", alarm.wakeMode)
                        putExtra("math_difficulty", alarm.mathDifficulty)
                        putExtra("delete_after_ringing", alarm.deleteAfterRinging)
                    }
                    context.startActivity(ringIntent)
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