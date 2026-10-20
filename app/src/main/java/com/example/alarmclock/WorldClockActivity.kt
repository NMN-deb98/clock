package com.example.alarmclock

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
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
    private val zones = linkedMapOf(
        "تهران · ایران" to "Asia/Tehran",
        "استانبول · ترکیه" to "Europe/Istanbul",
        "لندن · بریتانیا" to "Europe/London",
        "پاریس · فرانسه" to "Europe/Paris",
        "نیویورک · آمریکا" to "America/New_York",
        "لس‌آنجلس · آمریکا" to "America/Los_Angeles",
        "دبی · امارات" to "Asia/Dubai",
        "توکیو · ژاپن" to "Asia/Tokyo",
        "سئول · کره جنوبی" to "Asia/Seoul",
        "دهلی · هند" to "Asia/Kolkata",
        "سیدنی · استرالیا" to "Australia/Sydney",
        "سنگاپور" to "Asia/Singapore",
        "تورنتو · کانادا" to "America/Toronto",
        "مسکو · روسیه" to "Europe/Moscow",
        "ریاض · عربستان" to "Asia/Riyadh",
        "کابل · افغانستان" to "Asia/Kabul"
    )
    private val selected = linkedSetOf<String>()
    private val allZones: Map<String, String> by lazy {
        val available = TimeZone.getAvailableIDs().associate { it.replace('_', ' ') to it }
        (available + zones).toSortedMap()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected.addAll(prefs.getStringSet("zones", setOf("Asia/Tehran", "Europe/London", "America/New_York")) ?: emptySet())
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(0xFF080808.toInt())
        }
        root.addView(TextView(this).apply {
            text = "ساعت جهانی"; textSize = 26f; setTextColor(-1)
        })
        spinner = Spinner(this)
        val labels = allZones.keys.toList()
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        root.addView(spinner, ViewGroup.LayoutParams(-1, -2))
        root.addView(Button(this).apply {
            text = "افزودن شهر"
            setOnClickListener {
                val zone = allZones[labels[spinner.selectedItemPosition]] ?: return@setOnClickListener
                selected.add(zone)
                persist()
                render()
            }
        })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply { text = "بازگشت"; setOnClickListener { finish() } })
        setContentView(root)
        render()
    }

    private fun persist() { prefs.edit().putStringSet("zones", selected.toSet()).apply() }

    private fun render() {
        list.removeAllViews()
        selected.forEach { id ->
            val city = allZones.entries.firstOrNull { it.value == id }?.key ?: id
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 18, 0, 18)
            }
            val time = TextView(this).apply {
                textSize = 24f
                setTextColor(-1)
                tag = id
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@WorldClockActivity).apply { text = city; textSize = 16f; setTextColor(0xFFBDBDBD.toInt()) })
                addView(time)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply {
                text = "حذف"
                setOnClickListener { selected.remove(id); persist(); render() }
            })
            list.addView(row)
        }
        updateTimes()
    }

    private fun updateTimes() {
        for (i in 0 until list.childCount) {
            val row = list.getChildAt(i) as LinearLayout
            val column = row.getChildAt(0) as LinearLayout
            val time = column.getChildAt(1) as TextView
            val zone = time.tag as String
            val formatter = SimpleDateFormat("HH:mm:ss  ·  EEE, dd MMM", Locale.getDefault())
            formatter.timeZone = TimeZone.getTimeZone(zone)
            time.text = formatter.format(Date())
        }
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1000)
    }
    private val tick = Runnable { updateTimes() }
    override fun onDestroy() { handler.removeCallbacks(tick); super.onDestroy() }
}
