package com.example.alarmclock

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var animator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.app_background)
        window.navigationBarColor = getColor(R.color.app_background)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(getColor(R.color.app_background))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(TickingClockView(this), LinearLayout.LayoutParams(220, 220))
        root.addView(TextView(this).apply {
            text = "هوشیار"
            textSize = 25f
            setTextColor(getColor(R.color.text_primary))
            gravity = android.view.Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 24, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "Hooshyar • هر لحظه، دقیق‌تر"
            textSize = 14f
            setTextColor(getColor(R.color.accent_cyan))
            gravity = android.view.Gravity.CENTER
        })
        setContentView(root)
        UiFx.enterAll(root, 100L, 170L)
        handler.postDelayed({
            if (!isFinishing) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }, 1800)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        animator?.cancel()
        super.onDestroy()
    }

    private inner class TickingClockView(context: android.content.Context) : View(context) {
        private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.parseColor("#2B3A54") }
        private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.parseColor("#64E6D2") }
        private val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7f; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#9A8CFF") }
        private val center = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.parseColor("#64E6D2") }
        private var angle = 0f
        init {
            animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 450
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { angle = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) * 0.42f
            canvas.drawCircle(cx, cy, radius, face)
            for (i in 0 until 12) {
                canvas.save()
                canvas.rotate(i * 30f, cx, cy)
                canvas.drawLine(cx, cy - radius + 10f, cx, cy - radius + 23f, tick)
                canvas.restore()
            }
            canvas.save()
            canvas.rotate(angle, cx, cy)
            canvas.drawLine(cx, cy + radius * 0.22f, cx, cy - radius * 0.68f, hand)
            canvas.restore()
            canvas.drawCircle(cx, cy, 7f, center)
        }
    }
}