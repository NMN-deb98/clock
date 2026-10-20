package com.example.alarmclock

import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class CountdownTimerActivity : AppCompatActivity() {
    private lateinit var display: TextView
    private lateinit var minutesPicker: NumberPicker
    private lateinit var secondsPicker: NumberPicker
    private lateinit var startButton: Button
    private var timer: CountDownTimer? = null
    private var remainingMs = 0L
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF080808.toInt())
        }
        root.addView(TextView(this).apply { text = "تایمر شمارش معکوس"; textSize = 25f; setTextColor(-1) })
        display = TextView(this).apply {
            text = "00:00"; textSize = 48f; setTextColor(-1); gravity = Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }
        root.addView(display)
        val pickers = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        minutesPicker = NumberPicker(this).apply { minValue = 0; maxValue = 999; value = 1; wrapSelectorWheel = false }
        secondsPicker = NumberPicker(this).apply { minValue = 0; maxValue = 59; value = 0; wrapSelectorWheel = true }
        pickers.addView(minutesPicker, LinearLayout.LayoutParams(120, ViewGroup.LayoutParams.WRAP_CONTENT))
        pickers.addView(secondsPicker, LinearLayout.LayoutParams(120, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(pickers)
        startButton = Button(this).apply { text = "شروع"; setOnClickListener { toggle() } }
        root.addView(startButton)
        root.addView(Button(this).apply { text = "بازنشانی"; setOnClickListener { reset() } })
        root.addView(Button(this).apply { text = "بازگشت"; setOnClickListener { finish() } })
        setContentView(root)
        updateDisplay()
    }

    private fun toggle() {
        if (running) {
            timer?.cancel()
            running = false
            startButton.text = "ادامه"
            return
        }
        if (remainingMs <= 0L) remainingMs = (minutesPicker.value * 60L + secondsPicker.value) * 1000L
        if (remainingMs <= 0L) {
            android.widget.Toast.makeText(this, "مدت زمان را بیشتر از صفر انتخاب کن.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        minutesPicker.isEnabled = false
        secondsPicker.isEnabled = false
        running = true
        startButton.text = "توقف"
        timer = object : CountDownTimer(remainingMs, 250L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMs = millisUntilFinished
                updateDisplay()
            }
            override fun onFinish() {
                remainingMs = 0L
                running = false
                minutesPicker.isEnabled = true
                secondsPicker.isEnabled = true
                startButton.text = "شروع دوباره"
                updateDisplay()
                android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100).apply {
                    startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
                    android.os.Handler(mainLooper).postDelayed({ release() }, 1700)
                }
                android.app.AlertDialog.Builder(this@CountdownTimerActivity)
                    .setTitle("زمان تمام شد")
                    .setMessage("تایمر به پایان رسید.")
                    .setPositiveButton("باشه", null).show()
            }
        }.start()
    }

    private fun reset() {
        timer?.cancel()
        timer = null
        running = false
        remainingMs = 0L
        minutesPicker.isEnabled = true
        secondsPicker.isEnabled = true
        startButton.text = "شروع"
        updateDisplay()
    }

    private fun updateDisplay() {
        val totalSeconds = if (remainingMs > 0L) (remainingMs + 999L) / 1000L
            else minutesPicker.value * 60L + secondsPicker.value
        display.text = String.format(Locale.getDefault(), "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
