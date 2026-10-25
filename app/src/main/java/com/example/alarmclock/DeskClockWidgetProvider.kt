package com.example.alarmclock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Calendar

class DeskClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_DATE_CHANGED) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, DeskClockWidgetProvider::class.java))
                .forEach { updateWidget(context, manager, it) }
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_desk_clock)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        views.setTextViewText(R.id.widgetSunMoon, if (hour in 6..17) "☀️" else "🌙")
        views.setTextViewText(R.id.widgetDate, "Hooshyar • ساعت رومیزی")
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, DeskClockActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetTime, open)
        views.setOnClickPendingIntent(R.id.widgetSunMoon, open)
        manager.updateAppWidget(id, views)
    }
}