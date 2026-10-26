package com.example.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Calendar

/**
 * The launcher icon follows the time of day (dawn, day, dusk, night) by switching between
 * activity-aliases. Android can't animate a launcher icon, so it changes at the phase boundaries.
 */
object AppIconSwitcher {
    private val aliases = listOf("LauncherDawn", "LauncherDay", "LauncherDusk", "LauncherNight")
    private const val DEFAULT_ALIAS = "LauncherDay"

    fun isLive(context: Context): Boolean =
        context.getSharedPreferences("alarm_preferences", Context.MODE_PRIVATE).getBoolean("live_icon", true)

    fun setLive(context: Context, live: Boolean) {
        context.getSharedPreferences("alarm_preferences", Context.MODE_PRIVATE).edit().putBoolean("live_icon", live).apply()
        if (live) {
            apply(context)
            scheduleNext(context)
        } else {
            cancel(context)
            switchTo(context, DEFAULT_ALIAS)
        }
    }

    private fun aliasFor(hour: Int): String = when (hour) {
        in 5..7 -> "LauncherDawn"
        in 8..16 -> "LauncherDay"
        in 17..19 -> "LauncherDusk"
        else -> "LauncherNight"
    }

    fun apply(context: Context) {
        if (!isLive(context)) return
        switchTo(context, aliasFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))
    }

    private fun switchTo(context: Context, target: String) {
        val pm = context.packageManager
        fun component(name: String) = ComponentName(context.packageName, "com.example.alarmclock.$name")
        runCatching {
            val targetState = pm.getComponentEnabledSetting(component(target))
            val targetOn = targetState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (targetState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && target == DEFAULT_ALIAS)
            if (!targetOn) {
                pm.setComponentEnabledSetting(component(target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            }
            aliases.filter { it != target }.forEach { name ->
                val state = pm.getComponentEnabledSetting(component(name))
                val on = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && name == DEFAULT_ALIAS)
                if (on) pm.setComponentEnabledSetting(component(name), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 9, Intent(context, IconPhaseReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun scheduleNext(context: Context) {
        if (!isLive(context)) return
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 30); set(Calendar.MILLISECOND, 0)
            val boundaries = listOf(5, 8, 17, 20)
            val h = now.get(Calendar.HOUR_OF_DAY)
            val nextHour = boundaries.firstOrNull { it > h }
            if (nextHour == null) { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, boundaries.first()) } else set(Calendar.HOUR_OF_DAY, nextHour)
        }.timeInMillis
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { am.set(AlarmManager.RTC, next, pending(context)) }
    }

    private fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context))
    }
}

class IconPhaseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppIconSwitcher.apply(context)
        AppIconSwitcher.scheduleNext(context)
    }
}
