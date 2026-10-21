package com.example.alarmclock

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import kotlin.math.min

class CountdownTimerActivity : AppCompatActivity() {
    private lateinit var display: TextView
    private lateinit var status: TextView
    private lateinit var minutesPicker: NumberPicker
    private lateinit var secondsPicker: NumberPicker
    private lateinit var startButton: Button
    private lateinit var ring: TimerRingView
    private var timer: CountDownTimer? = null
    private var remainingMs = 300000L
    private var totalDurationMs = 300000L
    private var running = false
    private var completed = false

    private val bg by lazy { getColor(R.color.app_background) }
    private val surface by lazy { getColor(R.color.surface_primary) }
    private val elevated by lazy { getColor(R.color.surface_elevated) }
    private val border by lazy { getColor(R.color.surface_border) }
    private val primary by lazy { getColor(R.color.text_primary) }
    private val secondary by lazy { getColor(R.color.text_secondary) }
    private val cyan by lazy { getColor(R.color.accent_cyan) }
    private val violet by lazy { getColor(R.color.accent_violet) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(12))
            setBackgroundColor(bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        titleBlock.addView(text("زمان‌سنج", 27f, primary, true))
        titleBlock.addView(text("زمانت را مدیریت کن", 13f, secondary).apply { setPadding(0, dp(4), 0, 0) })
        header.addView(titleBlock)
        header.addView(TextView(this).apply {
            text = "◴"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(cyan)
            background = rounded(elevated, dp(16)); layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        })
        root.addView(header)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(surface, elevated)).apply {
                cornerRadius = dp(28).toFloat(); setStroke(dp(1), border)
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) }
        }
        val ringFrame = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(dp(252), dp(252)) }
        ring = TimerRingView().apply { setColors(border, cyan, violet) }
        ringFrame.addView(ring, FrameLayout.LayoutParams(-1, -1))
        val center = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        center.addView(text("زمان باقی‌مانده", 13f, secondary))
        display = text("۰۵:۰۰", 43f, primary, true).apply {
            gravity = Gravity.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false; setPadding(0, dp(8), 0, dp(5))
        }
        center.addView(display)
        status = text("آماده شروع", 13f, cyan, true)
        center.addView(status)
        ringFrame.addView(center, FrameLayout.LayoutParams(-1, -1))
        card.addView(ringFrame)
        card.addView(text("انتخاب سریع", 14f, primary, true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(12))
        })
        val presetRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        for (chunk in listOf(1, 5, 10, 15, 25, 30).chunked(3)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for (minutes in chunk) {
                val chip = Button(this).apply {
                    text = toPersian(minutes.toString()) + " دقیقه"; textSize = 12f; isAllCaps = false
                    setTextColor(primary); backgroundTintList = ColorStateList.valueOf(elevated)
                    insetTop = 0; insetBottom = 0; minHeight = dp(42)
                    setOnClickListener { choosePreset(minutes) }
                }
                row.addView(chip, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    marginStart = dp(4); marginEnd = dp(4); bottomMargin = dp(7)
                })
            }
            presetRows.addView(row)
        }
        card.addView(presetRows)
        root.addView(card)

        val pickerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = rounded(surface, dp(22), border)
            setPadding(dp(14), dp(12), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
        }
        pickerCard.addView(text("یا مدت دلخواهت را تنظیم کن", 14f, primary, true).apply {
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(6))
        })
        val labels = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        labels.addView(text("دقیقه", 12f, secondary).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        labels.addView(text("ثانیه", 12f, secondary).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        pickerCard.addView(labels)
        val pickers = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        minutesPicker = NumberPicker(this).apply {
            minValue = 0; maxValue = 999; value = 5; wrapSelectorWheel = false
            setOnValueChangedListener { _, _, _ -> if (!running) syncFromPickers() }
        }
        secondsPicker = NumberPicker(this).apply {
            minValue = 0; maxValue = 59; value = 0; wrapSelectorWheel = true
            setOnValueChangedListener { _, _, _ -> if (!running) syncFromPickers() }
        }
        pickers.addView(minutesPicker, LinearLayout.LayoutParams(0, dp(90), 1f))
        pickers.addView(secondsPicker, LinearLayout.LayoutParams(0, dp(90), 1f))
        pickerCard.addView(pickers)
        root.addView(pickerCard)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
        }
        startButton = actionButton("شروع تایمر", cyan, bg)
        controls.addView(startButton, LinearLayout.LayoutParams(0, dp(56), 1.5f).apply { marginEnd = dp(4) })
        startButton.setOnClickListener { toggle() }
        val reset = actionButton("بازنشانی", elevated, primary)
        controls.addView(reset, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(8) })
        reset.setOnClickListener { resetTimer() }
        root.addView(controls)
        root.addView(Button(this).apply {
            text = "بازگشت"; isAllCaps = false; setTextColor(secondary)
            backgroundTintList = ColorStateList.valueOf(bg); setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(4) })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
        syncFromPickers()
    }

    private fun choosePreset(minutes: Int) {
        if (running) return
        minutesPicker.value = minutes; secondsPicker.value = 0
        remainingMs = minutes * 60000L; totalDurationMs = remainingMs; completed = false
        updateDisplay(); updateControls()
    }

    private fun syncFromPickers() {
        if (running) return
        remainingMs = (minutesPicker.value * 60L + secondsPicker.value) * 1000L
        totalDurationMs = remainingMs; completed = false
        updateDisplay(); updateControls()
    }

    private fun toggle() {
        if (running) {
            timer?.cancel(); running = false; updateControls(); updateDisplay(); return
        }
        if (completed) { remainingMs = totalDurationMs; completed = false }
        if (remainingMs <= 0L) {
            remainingMs = (minutesPicker.value * 60L + secondsPicker.value) * 1000L
            totalDurationMs = remainingMs
        }
        if (remainingMs <= 0L) {
            Toast.makeText(this, "مدت زمان را بیشتر از صفر انتخاب کن.", Toast.LENGTH_SHORT).show(); return
        }
        if (totalDurationMs <= 0L) totalDurationMs = remainingMs
        running = true; updateControls(); updateDisplay()
        timer?.cancel()
        timer = object : CountDownTimer(remainingMs, 100L) {
            override fun onTick(millisUntilFinished: Long) { remainingMs = millisUntilFinished; updateDisplay() }
            override fun onFinish() {
                remainingMs = 0L; running = false; completed = true
                updateDisplay(); updateControls()
                try {
                    val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
                    tone.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
                    android.os.Handler(mainLooper).postDelayed({ tone.release() }, 1700)
                } catch (_: Exception) { }
                AlertDialog.Builder(this@CountdownTimerActivity).setTitle("زمان تمام شد")
                    .setMessage("تایمر به پایان رسید.").setPositiveButton("باشه", null).show()
            }
        }.start()
    }

    private fun resetTimer() {
        timer?.cancel(); timer = null; running = false; completed = false
        remainingMs = (minutesPicker.value * 60L + secondsPicker.value) * 1000L
        totalDurationMs = remainingMs
        updateDisplay(); updateControls()
    }

    private fun updateDisplay() {
        val seconds = if (remainingMs > 0L) (remainingMs + 999L) / 1000L
            else if (completed) 0L else minutesPicker.value * 60L + secondsPicker.value
        display.text = toPersian(String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L))
        ring.setProgress(if (totalDurationMs > 0L) remainingMs.toFloat() / totalDurationMs else 1f)
        status.text = when {
            completed -> "پایان زمان"
            running -> "در حال شمارش"
            remainingMs < totalDurationMs && remainingMs > 0L -> "متوقف شده"
            else -> "آماده شروع"
        }
        status.setTextColor(if (completed) violet else if (running) cyan else secondary)
    }

    private fun updateControls() {
        startButton.text = when {
            running -> "توقف"
            completed -> "شروع دوباره"
            remainingMs < totalDurationMs && remainingMs > 0L -> "ادامه"
            else -> "شروع تایمر"
        }
        startButton.backgroundTintList = ColorStateList.valueOf(if (running) violet else cyan)
        startButton.setTextColor(bg)
        minutesPicker.isEnabled = !running; secondsPicker.isEnabled = !running
    }

    private fun actionButton(label: String, color: Int, textColor: Int) = Button(this).apply {
        text = label; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; isAllCaps = false
        setTextColor(textColor); backgroundTintList = ColorStateList.valueOf(color)
        minHeight = dp(52); insetTop = 0; insetBottom = 0
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius.toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toPersian(value: String): String = value.map { if (it in '0'..'9') ('۰'.code + it - '0').toChar() else it }.joinToString("")

    override fun onDestroy() { timer?.cancel(); super.onDestroy() }

    private inner class TimerRingView : View(this@CountdownTimerActivity) {
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val bounds = RectF()
        private var trackColor = 0
        private var activeColor = 0
        private var doneColor = 0
        private var progress = 1f
        fun setColors(track: Int, active: Int, done: Int) { trackColor = track; activeColor = active; doneColor = done; invalidate() }
        fun setProgress(value: Float) { progress = value.coerceIn(0f, 1f); invalidate() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val stroke = dp(9).toFloat(); val side = min(width, height).toFloat(); val inset = stroke / 2 + dp(8)
            bounds.set((width-side)/2+inset, (height-side)/2+inset, (width+side)/2-inset, (height+side)/2-inset)
            track.color = trackColor; track.strokeWidth = stroke
            arc.color = if (completed) doneColor else activeColor; arc.strokeWidth = stroke
            canvas.drawArc(bounds, -90f, 360f, false, track)
            if (progress > 0f) canvas.drawArc(bounds, -90f, 360f * progress, false, arc)
        }
    }
}
