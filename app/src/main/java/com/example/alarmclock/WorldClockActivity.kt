package com.example.alarmclock

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class WorldClockActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var list: LinearLayout
    private lateinit var spinner: Spinner
    private val prefs by lazy { getSharedPreferences("world_clocks", MODE_PRIVATE) }

    // Use the device's complete IANA time-zone database rather than a short hard-coded list.
    private val allZones: List<ZoneChoice> by lazy {
        TimeZone.getAvailableIDs()
            .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
            .distinct()
            .map { id -> ZoneChoice(id, displayName(id)) }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }
    private val selected = linkedSetOf<String>()
    private val rows = linkedMapOf<String, ClockRow>()
    private data class ZoneChoice(val id: String, val label: String)
    private data class ClockRow(val time: TextView, val dayStatus: TextView)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected.addAll(prefs.getStringSet("zones", setOf("Asia/Tehran", "Europe/London", "America/New_York")) ?: emptySet())
        selected.retainAll(allZones.map { it.id }.toSet())

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 18)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(getColor(R.color.app_background))
        }
        root.addView(TextView(this).apply {
            text = "ساعت جهانی"
            textSize = 27f
            setTextColor(getColor(R.color.text_primary))
            setPadding(0, 0, 0, 6)
            gravity = Gravity.END
        })
        root.addView(TextView(this).apply {
            text = "زمان محلی شهرهای مختلف را زنده ببین"
            textSize = 13f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, 0, 0, 16)
            gravity = Gravity.END
        })

        spinner = Spinner(this).apply {
            background = getDrawable(R.drawable.bg_input_field)
            setPadding(12, 4, 12, 4)
        }
        val labels = allZones.map { it.label }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        root.addView(spinner, ViewGroup.LayoutParams(-1, -2))
        root.addView(Button(this).apply {
            text = "＋ افزودن شهر"
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.accent_cyan))
            setTextColor(getColor(R.color.app_background))
            setOnClickListener {
                val choice = allZones.getOrNull(spinner.selectedItemPosition) ?: return@setOnClickListener
                if (!selected.add(choice.id)) {
                    Toast.makeText(this@WorldClockActivity, "این شهر قبلاً اضافه شده است.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                persist()
                render()
            }
        })

        val scroll = ScrollView(this).apply { clipToPadding = false; setPadding(0, 12, 0, 8) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "بازگشت"
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.surface_elevated))
            setTextColor(getColor(R.color.text_primary))
            setOnClickListener { finish() }
        })
        setContentView(root)
        render()
    }

    private fun displayName(id: String): String {
        val parts = id.split('/')
        val city = parts.last().replace('_', ' ').replace('-', ' ')
        val region = parts.first().replace('_', ' ')
        // Avoid presenting a continent as if it were the country.
        val country = countryForZone(id)
        val known = mapOf(
            "Asia/Tehran" to "تهران · ایران 🇮🇷",
            "Europe/Istanbul" to "استانبول · ترکیه 🇹🇷",
            "Europe/London" to "لندن · بریتانیا 🇬🇧",
            "Europe/Paris" to "پاریس · فرانسه 🇫🇷",
            "America/New_York" to "نیویورک · آمریکا 🇺🇸",
            "America/Los_Angeles" to "لس‌آنجلس · آمریکا 🇺🇸",
            "America/Chicago" to "شیکاگو · آمریکا 🇺🇸",
            "America/Toronto" to "تورنتو · کانادا 🇨🇦",
            "America/Vancouver" to "ونکوور · کانادا 🇨🇦",
            "Asia/Dubai" to "دبی · امارات 🇦🇪",
            "Asia/Tokyo" to "توکیو · ژاپن 🇯🇵",
            "Asia/Seoul" to "سئول · کره جنوبی 🇰🇷",
            "Asia/Kolkata" to "دهلی · هند 🇮🇳",
            "Australia/Sydney" to "سیدنی · استرالیا 🇦🇺",
            "Asia/Singapore" to "سنگاپور 🇸🇬",
            "Europe/Moscow" to "مسکو · روسیه 🇷🇺",
            "Asia/Riyadh" to "ریاض · عربستان 🇸🇦",
            "Asia/Kabul" to "کابل · افغانستان 🇦🇫",
            "Asia/Shanghai" to "شانگهای · چین 🇨🇳",
            "Asia/Hong_Kong" to "هنگ‌کنگ 🇭🇰",
            "Asia/Bangkok" to "بانکوک · تایلند 🇹🇭",
            "Asia/Jakarta" to "جاکارتا · اندونزی 🇮🇩",
            "Europe/Berlin" to "برلین · آلمان 🇩🇪",
            "Europe/Rome" to "رم · ایتالیا 🇮🇹",
            "Europe/Madrid" to "مادرید · اسپانیا 🇪🇸",
            "Europe/Amsterdam" to "آمستردام · هلند 🇳🇱",
            "America/Sao_Paulo" to "سائوپائولو · برزیل 🇧🇷",
            "Africa/Cairo" to "قاهره · مصر 🇪🇬",
            "Africa/Johannesburg" to "ژوهانسبورگ · آفریقای جنوبی 🇿🇦",
            "Pacific/Auckland" to "اوکلند · نیوزیلند 🇳🇿"
        )
        return known[id] ?: if (country != null) "$city · ${country.first} ${country.second}" else "$city · منطقهٔ زمانی $region"
    }

    private fun countryForZone(id: String): Pair<String, String>? {
        val countryByZone = mapOf(
            "Asia/Tehran" to ("ایران" to "🇮🇷"), "Europe/Istanbul" to ("ترکیه" to "🇹🇷"),
            "Europe/London" to ("بریتانیا" to "🇬🇧"), "Europe/Paris" to ("فرانسه" to "🇫🇷"),
            "Europe/Berlin" to ("آلمان" to "🇩🇪"), "Europe/Rome" to ("ایتالیا" to "🇮🇹"),
            "Europe/Madrid" to ("اسپانیا" to "🇪🇸"), "Europe/Amsterdam" to ("هلند" to "🇳🇱"),
            "Europe/Moscow" to ("روسیه" to "🇷🇺"), "America/New_York" to ("آمریکا" to "🇺🇸"),
            "America/Los_Angeles" to ("آمریکا" to "🇺🇸"), "America/Chicago" to ("آمریکا" to "🇺🇸"),
            "America/Toronto" to ("کانادا" to "🇨🇦"), "America/Vancouver" to ("کانادا" to "🇨🇦"),
            "Asia/Dubai" to ("امارات" to "🇦🇪"), "Asia/Tokyo" to ("ژاپن" to "🇯🇵"),
            "Asia/Seoul" to ("کره جنوبی" to "🇰🇷"), "Asia/Kolkata" to ("هند" to "🇮🇳"),
            "Australia/Sydney" to ("استرالیا" to "🇦🇺"), "Asia/Singapore" to ("سنگاپور" to "🇸🇬"),
            "Asia/Riyadh" to ("عربستان سعودی" to "🇸🇦"), "Asia/Kabul" to ("افغانستان" to "🇦🇫"),
            "Asia/Shanghai" to ("چین" to "🇨🇳"), "Asia/Hong_Kong" to ("هنگ‌کنگ" to "🇭🇰"),
            "Asia/Bangkok" to ("تایلند" to "🇹🇭"), "Asia/Jakarta" to ("اندونزی" to "🇮🇩"),
            "America/Sao_Paulo" to ("برزیل" to "🇧🇷"), "Africa/Cairo" to ("مصر" to "🇪🇬"),
            "Africa/Johannesburg" to ("آفریقای جنوبی" to "🇿🇦"), "Pacific/Auckland" to ("نیوزیلند" to "🇳🇿")
        )
        countryByZone[id]?.let { return it }
        val prefixes = listOf("America/" to ("آمریکا" to "🇺🇸"), "Australia/" to ("استرالیا" to "🇦🇺"))
        return prefixes.firstOrNull { id.startsWith(it.first) }?.second
    }

    private fun persist() {
        prefs.edit().putStringSet("zones", selected.toSet()).apply()
    }

    private fun render() {
        list.removeAllViews()
        rows.clear()
        if (selected.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "هنوز شهری اضافه نکرده‌ای. از فهرست بالا یک شهر انتخاب کن."
                textSize = 14f
                setTextColor(getColor(R.color.text_secondary))
                gravity = Gravity.CENTER
                setPadding(16, 32, 16, 32)
            })
        }
        selected.forEach { id ->
            val choice = allZones.firstOrNull { it.id == id } ?: return@forEach
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(14, 16, 14, 16)
                background = getDrawable(R.drawable.bg_surface_card)
                elevation = 2f
            }
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            column.addView(TextView(this).apply {
                text = choice.label
                textSize = 15f
                setTextColor(getColor(R.color.text_primary))
                gravity = Gravity.END
            })
            val time = TextView(this).apply {
                textSize = 27f
                setTextColor(getColor(R.color.accent_cyan))
                gravity = Gravity.END
                typeface = android.graphics.Typeface.MONOSPACE
                includeFontPadding = false
                setPadding(0, 6, 0, 4)
            }
            column.addView(time)
            val status = TextView(this).apply {
                textSize = 12f
                setTextColor(getColor(R.color.text_secondary))
                gravity = Gravity.END
            }
            column.addView(status)
            row.addView(column)
            row.addView(Button(this).apply {
                text = "حذف"
                minWidth = 0
                backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.danger))
                setTextColor(getColor(R.color.app_background))
                setOnClickListener {
                    selected.remove(id)
                    persist()
                    render()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = 10
            })
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10
            })
            rows[id] = ClockRow(time, status)
        }
        updateTimes()
    }

    private fun updateTimes() {
        val now = Date()
        val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale("fa", "IR"))
        val dateFormatter = SimpleDateFormat("EEEE، dd MMMM yyyy", Locale("fa", "IR"))
        rows.forEach { (zone, row) ->
            val timeZone = TimeZone.getTimeZone(zone)
            timeFormatter.timeZone = timeZone
            dateFormatter.timeZone = timeZone
            row.time.text = timeFormatter.format(now)
            val hour = SimpleDateFormat("H", Locale.ROOT).apply { this.timeZone = timeZone }.format(now).toIntOrNull() ?: 12
            val isDay = hour in 6..17
            row.dayStatus.text = (if (isDay) "☀️ روز" else "🌙 شب") + "  •  " + dateFormatter.format(now)
            row.dayStatus.setTextColor(getColor(if (isDay) R.color.accent_cyan else R.color.text_secondary))
        }
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1000)
    }

    private val tick = Runnable { updateTimes() }
    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}
