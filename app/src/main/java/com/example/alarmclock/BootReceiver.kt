package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = AlarmDatabase.getInstance(context).alarmDao()
                    val alarms = dao.getAllEnabled()
                    alarms.forEach { AlarmScheduler.schedule(context, it) }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
