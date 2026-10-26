package com.example.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

/**
 * Home-screen widget: live clock, Persian date, the next alarm and shortcuts
 * (new alarm / alarms / desk mode). Its colors follow the time of day.
 */
class DeskClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refreshAll(context)
    }

    override fun onEnabled(context: Context) {
        scheduleNextRefresh(context)
    }

    override fun onDisabled(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(refreshIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED,
            ACTION_REFRESH, ACTION_PINNED -> refreshAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.alarmclock.action.WIDGET_REFRESH"
        const val ACTION_PINNED = "com.example.alarmclock.action.WIDGET_PINNED"

        private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 7, Intent(context, DeskClockWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /** Colors and icon change every hour, the date at midnight: wake up at the next full hour. */
        fun scheduleNextRefresh(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val next = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 5); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            runCatching { am.set(AlarmManager.RTC, next, refreshIntent(context)) }
        }

        /** Re-draws every widget instance (called when alarms change, hourly, and on time changes). */
        fun refreshAll(context: Context, delayMs: Long = 0L) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, DeskClockWidgetProvider::class.java))
            if (ids.isEmpty()) return
            scheduleNextRefresh(app)
            CoroutineScope(Dispatchers.IO).launch {
                if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
                val next = runCatching { nextAlarmText(app) }.getOrDefault("")
                ids.forEach { runCatching { manager.updateAppWidget(it, build(app, next)) } }
            }
        }

        private suspend fun nextAlarmText(context: Context): String {
            val alarms = AlarmDatabase.getInstance(context).alarmDao().getAllEnabled()
            val best = alarms.map { it to AlarmScheduler.nextTriggerTimeMillis(it) }.filter { it.second > 0L }.minByOrNull { it.second }
                ?: return "هشدار فعالی نداری"
            val now = Calendar.getInstance()
            val at = PersianUtils.newCalendar(TimeZone.getDefault(), best.second)
            val dayDiff = (best.second - now.timeInMillis) / 3_600_000L
            val day = when {
                at.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) && at.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> "امروز"
                dayDiff < 48 && at.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) + 1 -> "فردا"
                else -> PersianUtils.weekdayName(at.get(Calendar.DAY_OF_WEEK))
            }
            return "زنگ بعدی: " + day + " " + PersianUtils.two(at.get(Calendar.HOUR_OF_DAY)) + ":" + PersianUtils.two(at.get(Calendar.MINUTE))
        }

        private fun open(context: Context, code: Int, intent: Intent): PendingIntent = PendingIntent.getActivity(
            context, code, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun build(context: Context, nextText: String): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_desk_clock)
            val cal = PersianUtils.newCalendar(TimeZone.getDefault(), System.currentTimeMillis())
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val (background, icon) = when (hour) {
                in 5..7 -> R.drawable.widget_bg_dawn to R.drawable.ic_sun
                in 8..16 -> R.drawable.widget_bg_day to R.drawable.ic_sun
                in 17..19 -> R.drawable.widget_bg_dusk to R.drawable.ic_sun
                else -> R.drawable.widget_bg_night to R.drawable.ic_moon
            }
            views.setInt(R.id.widgetRoot, "setBackgroundResource", background)
            views.setImageViewResource(R.id.widgetIcon, icon)
            views.setTextViewText(R.id.widgetDate, PersianUtils.dateText(cal))
            views.setTextViewText(R.id.widgetNext, nextText)
            views.setOnClickPendingIntent(R.id.widgetTime, open(context, 1, Intent(context, MainActivity::class.java)))
            views.setOnClickPendingIntent(R.id.widgetNew, open(context, 2, Intent(context, AlarmEditActivity::class.java)))
            views.setOnClickPendingIntent(R.id.widgetAlarms, open(context, 3, Intent(context, MainActivity::class.java)))
            views.setOnClickPendingIntent(R.id.widgetDesk, open(context, 4, Intent(context, DeskClockActivity::class.java)))
            return views
        }
    }
}
