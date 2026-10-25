package com.example.alarmclock

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Showing the alarm over the lock screen needs a few things the user must allow.
 * This checks them one by one and knows which Settings page fixes each of them.
 */
object LockScreenReadiness {

    class Issue(val key: String, val title: String, val body: String)

    fun issues(context: Context): List<Issue> {
        val list = ArrayList<Issue>()
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            list += Issue(
                "notifications", "اعلان‌های برنامه خاموش است",
                "بدون اعلان، هشدار روی صفحهٔ قفل نشان داده نمی‌شود. اعلان‌ها را روشن کن."
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
            if (channel != null && channel.importance < NotificationManager.IMPORTANCE_HIGH) {
                list += Issue(
                    "channel", "اولویت اعلان هشدار پایین است",
                    "اولویت اعلان «هوشیار» را روی «بالا / نمایش پاپ‌آپ» بگذار تا صفحهٔ زنگ باز شود."
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!manager.canUseFullScreenIntent()) {
                list += Issue(
                    "fullscreen", "اجازهٔ نمایش تمام‌صفحه خاموش است",
                    "اندروید ۱۴ به بالا باید این اجازه را دستی بدهی تا هشدار روی صفحهٔ قفل باز شود."
                )
            }
        }
        if (!Settings.canDrawOverlays(context)) {
            list += Issue(
                "overlay", "اجازهٔ نمایش روی سایر برنامه‌ها (برای مطمئن‌تر شدن)",
                "با این اجازه، صفحهٔ زنگ حتی روی گوشی‌هایی که اعلان تمام‌صفحه را محدود می‌کنند باز می‌شود."
            )
        }
        return list
    }

    fun settingsIntent(context: Context, issue: Issue): Intent {
        val pkg = context.packageName
        val intent = when (issue.key) {
            "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            "channel" ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_ID)
                } else {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                }
            "fullscreen" ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(Uri.parse("package:$pkg"))
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$pkg"))
                }
            "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(Uri.parse("package:$pkg"))
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$pkg"))
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
