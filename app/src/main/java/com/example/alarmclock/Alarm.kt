package com.example.alarmclock

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * days: مجموعه‌ای از روزهای هفته که هشدار در آن‌ها تکرار می‌شود.
 * مقادیر مطابق java.util.Calendar: 1=یکشنبه ... 7=شنبه
 * اگر خالی باشد یعنی هشدار فقط یک‌بار زنگ می‌خورد و بعد از آن حذف می‌شود.
 */
@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var hour: Int,
    var minute: Int,
    var days: String = "",          // مثال: "2,3,4,5,6" جدا شده با کاما
    var label: String = "",         // یادداشت
    var ringtoneUri: String = "",   // Uri صدای زنگ به صورت رشته
    var ringtoneName: String = "پیش‌فرض سیستم",
    var vibrate: Boolean = true,
    var enabled: Boolean = true,
    var wakeMode: String = "NONE",
    var mathDifficulty: String = "MEDIUM"
) {
    fun dayList(): List<Int> =
        if (days.isBlank()) emptyList() else days.split(",").map { it.trim().toInt() }

    fun isRepeating(): Boolean = dayList().isNotEmpty()
}
