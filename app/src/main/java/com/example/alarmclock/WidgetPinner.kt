package com.example.alarmclock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/** Lets the user decide whether the clock widget goes to the home screen. */
object WidgetPinner {
    fun request(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, DeskClockWidgetProvider::class.java)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !manager.isRequestPinAppWidgetSupported) {
            AlertDialog.Builder(context)
                .setTitle("افزودن ویجت")
                .setMessage("روی صفحهٔ اصلی گوشی یک جای خالی را نگه دار، «ویجت‌ها» را بزن و «هوشیار» را انتخاب کن.")
                .setPositiveButton("متوجه شدم", null)
                .show()
            return
        }
        AlertDialog.Builder(context)
            .setTitle("ساعت روی صفحهٔ اصلی؟")
            .setMessage("ویجت ساعت، تاریخ شمسی و زنگ بعدی را روی صفحهٔ اصلی نشان می‌دهد و با زمان روز رنگ می‌گیرد. اضافه شود؟")
            .setPositiveButton("بله، اضافه کن") { _, _ ->
                val callback = PendingIntent.getBroadcast(
                    context, 0, Intent(context, DeskClockWidgetProvider::class.java).setAction(DeskClockWidgetProvider.ACTION_PINNED),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val ok = runCatching { manager.requestPinAppWidget(provider, null, callback) }.getOrDefault(false)
                if (!ok) Toast.makeText(context, "افزودن ویجت ممکن نشد؛ از منوی ویجت‌های گوشی اضافه کن.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("نه، فعلاً نه", null)
            .show()
    }
}
