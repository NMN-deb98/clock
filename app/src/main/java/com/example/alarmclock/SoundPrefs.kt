package com.example.alarmclock

import android.content.Context

/**
 * Per-alarm sound options (volume, gradual volume, loud escalation, holidays).
 * They are edited inside the alarm edit screen; older global values are only a fallback.
 */
object SoundPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("alarm_preferences", Context.MODE_PRIVATE)

    fun volume(context: Context, alarmId: Int): Int {
        val p = prefs(context)
        return p.getInt("snd_vol_$alarmId", p.getInt("volume", 75)).coerceIn(10, 100)
    }

    fun gradual(context: Context, alarmId: Int): Boolean {
        val p = prefs(context)
        return p.getBoolean("snd_grad_$alarmId", p.getBoolean("gradual_volume", true))
    }

    fun loud(context: Context, alarmId: Int): Boolean {
        val p = prefs(context)
        return p.getBoolean("snd_loud_$alarmId", p.getBoolean("loud_escalation", false))
    }

    fun skipHolidays(context: Context, alarmId: Int): Boolean {
        val p = prefs(context)
        return p.getBoolean("snd_hol_$alarmId", p.getBoolean("disable_holidays", false))
    }

    fun save(context: Context, alarmId: Int, volume: Int, gradual: Boolean, loud: Boolean, holidays: Boolean) {
        prefs(context).edit()
            .putInt("snd_vol_$alarmId", volume.coerceIn(10, 100))
            .putBoolean("snd_grad_$alarmId", gradual)
            .putBoolean("snd_loud_$alarmId", loud)
            .putBoolean("snd_hol_$alarmId", holidays)
            .apply()
    }
}
