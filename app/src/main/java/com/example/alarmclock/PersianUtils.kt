package com.example.alarmclock

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Small, dependency-free helpers for Persian digits and the Jalali (Shamsi) calendar.
 * Everything here is deterministic and does not depend on the device locale.
 */
object PersianUtils {
    private val monthNames = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    fun digits(value: String): String {
        val out = StringBuilder(value.length)
        for (ch in value) {
            out.append(if (ch in '0'..'9') ('۰'.code + (ch - '0')).toChar() else ch)
        }
        return out.toString()
    }

    fun digits(value: Int): String = digits(value.toString())

    fun digits(value: Long): String = digits(value.toString())

    /** Two-digit Persian number, e.g. 5 -> ۰۵. */
    fun two(value: Int): String = digits(if (value in 0..9) "0$value" else value.toString())

    /** [dayOfWeek] uses java.util.Calendar constants (SUNDAY = 1 ... SATURDAY = 7). */
    fun weekdayName(dayOfWeek: Int): String = when (dayOfWeek) {
        Calendar.SATURDAY -> "شنبه"
        Calendar.SUNDAY -> "یکشنبه"
        Calendar.MONDAY -> "دوشنبه"
        Calendar.TUESDAY -> "سه‌شنبه"
        Calendar.WEDNESDAY -> "چهارشنبه"
        Calendar.THURSDAY -> "پنجشنبه"
        else -> "جمعه"
    }

    fun monthName(jalaliMonth: Int): String = monthNames[(jalaliMonth - 1).coerceIn(0, 11)]

    /** Returns [year, month, day] in the Jalali calendar. [gm] is 1-based. */
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
            ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return intArrayOf(jy, jm, jd)
    }

    fun newCalendar(zone: TimeZone, millis: Long): Calendar {
        val cal = GregorianCalendar(zone)
        cal.timeInMillis = millis
        return cal
    }

    /** e.g. "یکشنبه، ۳ آبان ۱۴۰۵" */
    fun dateText(cal: Calendar, withWeekday: Boolean = true): String {
        val j = gregorianToJalali(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        val core = digits(j[2]) + " " + monthName(j[1]) + " " + digits(j[0])
        return if (withWeekday) weekdayName(cal.get(Calendar.DAY_OF_WEEK)) + "، " + core else core
    }

    fun greeting(hourOfDay: Int): String = when (hourOfDay) {
        in 5..11 -> "صبح بخیر"
        in 12..15 -> "ظهر بخیر"
        in 16..18 -> "عصر بخیر"
        else -> "شب بخیر"
    }

    /** "۲ ساعت و ۳۰ دقیقه" for a non-negative number of minutes. */
    fun hoursAndMinutes(totalMinutes: Long): String {
        val minutes = if (totalMinutes < 0) 0L else totalMinutes
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> digits(h) + " ساعت و " + digits(m) + " دقیقه"
            h > 0 -> digits(h) + " ساعت"
            else -> digits(m) + " دقیقه"
        }
    }
}
