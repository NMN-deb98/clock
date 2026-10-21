package com.example.alarmclock

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * days: مجموعه‌ای از روزهای هفته که هشدار در آن‌ها تکرار می‌شود.
 * مقادیر مطابق java.util.Calendar: 1=یکشنبه ... 7=شنبه
 */
@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var hour: Int,
    var minute: Int,
    var days: String = "",
    var label: String = "",
    var ringtoneUri: String = "",
    var ringtoneName: String = "پیش‌فرض سیستم",
    var vibrate: Boolean = true,
    var enabled: Boolean = true,
    var wakeMode: String = "NONE",
    var mathDifficulty: String = "MEDIUM",
    var deleteAfterRinging: Boolean = false
) {
    fun dayList(): List<Int> =
        if (days.isBlank()) emptyList() else days.split(",").map { it.trim().toInt() }

    fun isRepeating(): Boolean = dayList().isNotEmpty()
}