package com.example.alarmclock

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class StopwatchActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var startedAt = 0L
    private var accumulated = 0L
    private lateinit var display: TextView
    private lateinit var lapList: LinearLayout
    private var lap = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 36, 24, 24)
            setBackgroundColor(getColor(R.color.app_background))
        }
        root.addView(TextView(this).apply { text = "کرنومتر"; textSize = 27f; setTextColor(getColor(R.color.text_primary)); setPadding(0, 0, 0, 16) })
        display = TextView(this).apply {
            text = "00:00.00"; textSize = 42f; setTextColor(getColor(R.color.accent_cyan)); gravity = Gravity.CENTER
            setPadding(0, 48, 0, 48); setBackgroundResource(R.drawable.bg_surface_card)
        }
        root.addView(display)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        buttons.addView(Button(this).apply { text = "شروع / توقف"; backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.accent_cyan)); setTextColor(getColor(R.color.app_background)); setOnClickListener { toggle() } })
        buttons.addView(Button(this).apply { text = "دور"; backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.accent_violet)); setTextColor(getColor(R.color.app_background)); setOnClickListener { addLap() } })
        buttons.addView(Button(this).apply { text = "بازنشانی"; backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.surface_elevated)); setTextColor(getColor(R.color.text_primary)); setOnClickListener { reset() } })
        root.addView(buttons)
        lapList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(lapList)
        root.addView(Button(this).apply { text = "بازگشت"; backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.surface_elevated)); setTextColor(getColor(R.color.text_primary)); setOnClickListener { finish() } })
        setContentView(root)
    }

    private fun toggle() {
        if (running) { accumulated += SystemClock.elapsedRealtime() - startedAt; running = false }
        else { startedAt = SystemClock.elapsedRealtime(); running = true; handler.post(tick) }
        if (!running) handler.removeCallbacks(tick)
        updateDisplay()
    }
    private fun reset() {
        running = false; accumulated = 0L; lap = 0
        handler.removeCallbacks(tick); lapList.removeAllViews(); updateDisplay()
    }
    private fun addLap() {
        val now = elapsed()
        lap++
        lapList.addView(TextView(this).apply {
            text = String.format(Locale.getDefault(), "دور %d     %s", lap, format(now))
            textSize = 18f; setTextColor(getColor(R.color.text_primary)); setPadding(14, 12, 14, 12); setBackgroundResource(R.drawable.bg_surface_card)
        }, 0)
    }
    private fun elapsed() = accumulated + if (running) SystemClock.elapsedRealtime() - startedAt else 0L
    private fun updateDisplay() { display.text = format(elapsed()) }
    private fun format(ms: Long): String {
        val centis = (ms / 10) % 100
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 60000) % 60
        val hours = ms / 3600000
        return if (hours > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d.%02d", hours, minutes, seconds, centis)
        else String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, centis)
    }
    private val tick = object : Runnable {
        override fun run() { updateDisplay(); if (running) handler.postDelayed(this, 30) }
    }
    override fun onDestroy() { handler.removeCallbacks(tick); super.onDestroy() }
}
