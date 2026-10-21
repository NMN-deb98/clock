package com.example.alarmclock

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class StopwatchActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var startedAt = 0L
    private var accumulated = 0L
    private var lap = 0
    private var lastLapElapsed = 0L

    private lateinit var display: TextView
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private lateinit var lapButton: Button
    private lateinit var lapList: LinearLayout
    private lateinit var lapCount: TextView
    private lateinit var bestLapValue: TextView
    private lateinit var lastLapValue: TextView
    private val lapTimes = mutableListOf<Long>()

    private val cyan by lazy { getColor(R.color.accent_cyan) }
    private val violet by lazy { getColor(R.color.accent_violet) }
    private val primaryText by lazy { getColor(R.color.text_primary) }
    private val secondaryText by lazy { getColor(R.color.text_secondary) }
    private val surface by lazy { getColor(R.color.surface_primary) }
    private val elevated by lazy { getColor(R.color.surface_elevated) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.app_background)
        window.navigationBarColor = getColor(R.color.app_background)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
            setBackgroundColor(getColor(R.color.app_background))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        titleBlock.addView(label("کرنومتر", 27f, primaryText, true))
        titleBlock.addView(label("هر ثانیه، دقیق و مرتب", 13f, secondaryText).apply { setPadding(0, dp(3), 0, 0) })
        header.addView(titleBlock)
        header.addView(TextView(this).apply {
            text = "◷"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(cyan)
            background = rounded(elevated, dp(16))
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        })
        root.addView(header)

        val timerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(24), dp(12), dp(22))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(getColor(R.color.surface_primary), getColor(R.color.surface_elevated))
            ).apply {
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), getColor(R.color.surface_border))
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) }
        }
        val liveRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        liveRow.addView(View(this).apply {
            background = rounded(cyan, dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply { marginEnd = dp(7) }
        })
        status = label("آماده شروع", 13f, cyan, true)
        liveRow.addView(status)
        timerCard.addView(liveRow)

        timerCard.addView(label("دقیقه : ثانیه . صدم‌ثانیه", 12f, secondaryText).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })
        display = label("۰۰:۰۰.۰۰", 43f, primaryText, true).apply {
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_LTR
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.015f
            setPadding(0, dp(12), 0, dp(8))
            includeFontPadding = false
        }
        timerCard.addView(display)
        root.addView(timerCard)

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
        }
        val count = statCard("تعداد دورها", "۰", true)
        lapCount = count.second
        stats.addView(count.first, LinearLayout.LayoutParams(0, dp(78), 1f))
        stats.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(10), 1) })
        val best = statCard("بهترین دور", "—", false)
        bestLapValue = best.second
        stats.addView(best.first, LinearLayout.LayoutParams(0, dp(78), 1f))
        stats.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(10), 1) })
        val last = statCard("آخرین دور", "—", false)
        lastLapValue = last.second
        stats.addView(last.first, LinearLayout.LayoutParams(0, dp(78), 1f))
        root.addView(stats)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) }
        }
        startButton = actionButton("شروع", cyan, getColor(R.color.app_background))
        controls.addView(startButton, LinearLayout.LayoutParams(0, dp(56), 1.35f).apply { marginEnd = dp(6) })
        startButton.setOnClickListener { toggle() }

        lapButton = actionButton("ثبت دور", violet, getColor(R.color.app_background)).apply { isEnabled = false; alpha = 0.55f }
        controls.addView(lapButton, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(6); marginEnd = dp(6) })
        lapButton.setOnClickListener { addLap() }

        val resetButton = actionButton("↺", elevated, primaryText)
        resetButton.contentDescription = "بازنشانی کرنومتر"
        controls.addView(resetButton, LinearLayout.LayoutParams(dp(58), dp(56)).apply { marginStart = dp(6) })
        resetButton.setOnClickListener { reset() }
        root.addView(controls)

        val lapHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24); bottomMargin = dp(10) }
        }
        lapHeader.addView(label("دورهای ثبت‌شده", 17f, primaryText, true), LinearLayout.LayoutParams(0, -2, 1f))
        lapHeader.addView(label("زمان هر دور", 12f, secondaryText))
        root.addView(lapHeader)

        val lapScroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        lapList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lapScroll.addView(lapList)
        root.addView(lapScroll)

        val back = Button(this).apply {
            text = "بازگشت"
            setTextColor(secondaryText)
            backgroundTintList = android.content.res.ColorStateList.valueOf(surface)
            setOnClickListener { finish() }
        }
        root.addView(back, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        setContentView(root)
    }

    private fun statCard(title: String, value: String, highlight: Boolean): Pair<LinearLayout, TextView> {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(surface, dp(18), getColor(R.color.surface_border))
            setPadding(dp(5), dp(9), dp(5), dp(9))
        }
        card.addView(label(title, 11f, secondaryText))
        val valueView = label(value, 17f, if (highlight) cyan else primaryText, true).apply { setPadding(0, dp(5), 0, 0) }
        card.addView(valueView)
        return card to valueView
    }

    private fun actionButton(textValue: String, backgroundColor: Int, textColor: Int) =
        Button(this).apply {
            text = textValue
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            background = rounded(backgroundColor, dp(24), getColor(R.color.surface_border))
            isAllCaps = false
            minHeight = dp(52)
        }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toggle() {
        if (running) {
            accumulated += SystemClock.elapsedRealtime() - startedAt
            running = false
        } else {
            startedAt = SystemClock.elapsedRealtime()
            running = true
            handler.post(tick)
        }
        if (!running) handler.removeCallbacks(tick)
        refreshControls()
        updateDisplay()
    }

    private fun reset() {
        running = false
        accumulated = 0L
        lap = 0
        lastLapElapsed = 0L
        lapTimes.clear()
        handler.removeCallbacks(tick)
        lapList.removeAllViews()
        lapCount.text = "۰"
        bestLapValue.text = "—"
        lastLapValue.text = "—"
        refreshControls()
        updateDisplay()
    }

    private fun addLap() {
        if (!running && accumulated == 0L) return
        val now = elapsed()
        val split = now - lastLapElapsed
        lastLapElapsed = now
        lap++
        lapTimes.add(split)
        lapCount.text = toPersianDigits(lap.toString())
        lastLapValue.text = format(split)
        bestLapValue.text = format(lapTimes.minOrNull() ?: split)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(surface, dp(16), getColor(R.color.surface_border))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        }
        row.addView(label("دور " + toPersianDigits(lap.toString()), 14f, primaryText, true), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label(format(split), 16f, if (split == (lapTimes.minOrNull() ?: split)) cyan else primaryText, true))
        row.addView(label(format(now), 12f, secondaryText).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
        lapList.addView(row, 0)
    }

    private fun refreshControls() {
        startButton.text = when {
            running -> "توقف"
            accumulated > 0L -> "ادامه"
            else -> "شروع"
        }
        status.text = when {
            running -> "در حال شمارش"
            accumulated > 0L -> "متوقف شده"
            else -> "آماده شروع"
        }
        status.setTextColor(if (running) cyan else secondaryText)
        startButton.background = rounded(if (running) violet else cyan, dp(24), getColor(R.color.surface_border))
        startButton.setTextColor(getColor(R.color.app_background))
        lapButton.isEnabled = running || accumulated > 0L
        lapButton.alpha = if (lapButton.isEnabled) 1f else 0.55f
    }

    private fun elapsed() = accumulated + if (running) SystemClock.elapsedRealtime() - startedAt else 0L
    private fun updateDisplay() {
        display.text = toPersianDigits(format(elapsed()))
        val hue = ((android.os.SystemClock.uptimeMillis() / 40L) % 360L).toFloat()
        display.setTextColor(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.72f, 1f)))
    }

    private fun format(ms: Long): String {
        val centis = (ms / 10) % 100
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 60000) % 60
        val hours = ms / 3600000
        return if (hours > 0) String.format(Locale.US, "%02d:%02d:%02d.%02d", hours, minutes, seconds, centis)
        else String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
    }

    private fun toPersianDigits(value: String): String = value.map { ch ->
        if (ch in '0'..'9') ('۰'.code + (ch - '0')).toChar() else ch
    }.joinToString("")

    private val tick = object : Runnable {
        override fun run() {
            updateDisplay()
            if (running) handler.postDelayed(this, 30)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}
