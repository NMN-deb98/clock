package com.example.alarmclock

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * "ساعت رومیزی" - a full-screen bedside / desk clock.
 *  - tap: next colour theme
 *  - swipe up / down: screen brightness
 *  - the clock drifts a few pixels every minute to protect OLED screens
 */
class DeskClockActivity : BaseActivity() {

    private class DeskTheme(val name: String, val accent: Int, val top: Int, val bottom: Int)

    private val themes = listOf(
        DeskTheme("فیروزه‌ای", Color.parseColor("#64E6D2"), Color.parseColor("#04070D"), Color.parseColor("#0C1A2B")),
        DeskTheme("بنفش", Color.parseColor("#B3A7FF"), Color.parseColor("#06040E"), Color.parseColor("#1B1140")),
        DeskTheme("کهربایی", Color.parseColor("#FFC46B"), Color.parseColor("#080502"), Color.parseColor("#26170A")),
        DeskTheme("رز", Color.parseColor("#FF8FB1"), Color.parseColor("#0A0407"), Color.parseColor("#2C0F1D")),
        DeskTheme("سبز", Color.parseColor("#7DFFB0"), Color.parseColor("#020806"), Color.parseColor("#0B2418")),
        DeskTheme("شب‌خواب", Color.parseColor("#FF5C5C"), Color.BLACK, Color.BLACK)
    )

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("desk_clock", MODE_PRIVATE) }

    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var greetingView: TextView
    private lateinit var timeView: TextView
    private lateinit var dateView: TextView
    private lateinit var alarmView: TextView
    private lateinit var dots: SecondsDotsView
    private lateinit var closeButton: ImageButton
    private lateinit var flashView: TextView

    private var themeIndex = 0
    private var accent = Color.WHITE
    private var brightness = -1f
    private var lastMinuteKey = -1
    private var latestAlarms: List<Alarm> = emptyList()

    private val softWhite = Color.argb(150, 255, 255, 255)

    private val hideFlash = Runnable { flashView.animate().alpha(0f).setDuration(350L).start() }
    private val hideChrome = Runnable { closeButton.animate().alpha(0f).setDuration(500L).start() }

    private val detector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                cycleTheme()
                showChrome()
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (abs(distanceY) > abs(distanceX) && root.height > 0) {
                    adjustBrightness(distanceY / root.height)
                    return true
                }
                return false
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        themeIndex = prefs.getInt("theme", 0).coerceIn(0, themes.size - 1)

        buildUi()
        setContentView(root)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val viewModel = ViewModelProvider(this)[AlarmViewModel::class.java]
        viewModel.allAlarms.observe(this) { alarms ->
            latestAlarms = alarms.orEmpty()
            updateAlarmLine(System.currentTimeMillis())
        }

        root.post { fitClock() }
        UiFx.enter(content, 80L, 10f)
        showChrome()
        flash("لمس کن: رنگ بعدی  •  بالا/پایین بکش: نور صفحه", 4500L)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildUi() {
        val theme = themes[themeIndex]
        root = FrameLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = backgroundFor(theme)
            setOnTouchListener { _, event ->
                detector.onTouchEvent(event)
                true
            }
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) fitClock()
            }
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        greetingView = TextView(this).apply {
            setTextColor(softWhite)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        timeView = TextView(this).apply {
            typeface = UiFx.font(this@DeskClockActivity, 300)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
            maxLines = 1
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_LTR
            textSize = 80f
            setPadding(0, dp(6), 0, dp(6))
        }
        dateView = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
        }
        dots = SecondsDotsView(this)
        alarmView = TextView(this).apply {
            setTextColor(softWhite)
            textSize = 15f
            gravity = Gravity.CENTER
        }
        content.addView(greetingView, LinearLayout.LayoutParams(-1, -2))
        content.addView(timeView, LinearLayout.LayoutParams(-1, -2))
        content.addView(dateView, LinearLayout.LayoutParams(-1, -2))
        content.addView(dots, LinearLayout.LayoutParams(-1, dp(16)).apply {
            topMargin = dp(22)
            bottomMargin = dp(18)
        })
        content.addView(alarmView, LinearLayout.LayoutParams(-1, -2))
        root.addView(content, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))

        closeButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = UiFx.rounded(this@DeskClockActivity, Color.argb(50, 255, 255, 255), 22)
            contentDescription = "بستن ساعت رومیزی"
            setOnClickListener { finish() }
        }
        root.addView(
            closeButton,
            FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.START).apply {
                topMargin = dp(28)
                marginStart = dp(20)
            }
        )

        flashView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            alpha = 0f
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = UiFx.rounded(this@DeskClockActivity, Color.argb(70, 255, 255, 255), 20)
        }
        root.addView(
            flashView,
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(40)
            }
        )

        setAccent(theme.accent)
    }

    private fun backgroundFor(theme: DeskTheme) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(theme.top, theme.bottom)
    )

    private fun setAccent(color: Int) {
        accent = color
        timeView.setTextColor(color)
        dateView.setTextColor(Color.argb(210, Color.red(color), Color.green(color), Color.blue(color)))
        dots.accent = color
    }

    private fun cycleTheme() {
        themeIndex = (themeIndex + 1) % themes.size
        prefs.edit().putInt("theme", themeIndex).apply()
        val theme = themes[themeIndex]
        val newBackground = backgroundFor(theme)
        val old = root.background ?: newBackground
        val transition = TransitionDrawable(arrayOf(old, newBackground))
        root.background = transition
        transition.startTransition(450)
        ValueAnimator.ofObject(ArgbEvaluator(), accent, theme.accent).apply {
            duration = 450L
            addUpdateListener { setAccent(it.animatedValue as Int) }
            start()
        }
        flash(theme.name, 1200L)
    }

    private fun adjustBrightness(delta: Float) {
        if (brightness < 0f) brightness = currentBrightness()
        brightness = (brightness + delta * 1.4f).coerceIn(0.01f, 1f)
        val params = window.attributes
        params.screenBrightness = brightness
        window.attributes = params
        flash("نور صفحه: " + PersianUtils.digits((brightness * 100).toInt()) + "٪", 900L)
    }

    private fun currentBrightness(): Float = try {
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (_: Exception) {
        0.5f
    }

    private fun flash(text: String, durationMs: Long) {
        flashView.text = text
        flashView.animate().cancel()
        flashView.alpha = 1f
        handler.removeCallbacks(hideFlash)
        handler.postDelayed(hideFlash, durationMs)
    }

    private fun showChrome() {
        closeButton.animate().cancel()
        closeButton.alpha = 0.85f
        handler.removeCallbacks(hideChrome)
        handler.postDelayed(hideChrome, 3500L)
    }

    /** Sizes the big clock so it always fills the screen width (or ~42% of the height). */
    private fun fitClock() {
        val availableWidth = root.width - dp(48)
        val availableHeight = root.height
        if (availableWidth <= 0 || availableHeight <= 0) return
        val probe = Paint(timeView.paint)
        probe.textSize = 100f
        val unit = probe.measureText("۸۸:۸۸")
        if (unit <= 0f) return
        val size = min(availableWidth / unit * 100f, availableHeight * 0.42f)
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        greetingView.setTextSize(TypedValue.COMPLEX_UNIT_PX, (size * 0.10f).coerceIn(dp(13).toFloat(), dp(26).toFloat()))
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_PX, (size * 0.13f).coerceIn(dp(15).toFloat(), dp(34).toFloat()))
        alarmView.setTextSize(TypedValue.COMPLEX_UNIT_PX, (size * 0.085f).coerceIn(dp(12).toFloat(), dp(22).toFloat()))
    }

    // ---------------------------------------------------------------- Ticking

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            val now = System.currentTimeMillis()
            handler.postDelayed(this, 1000L - (now % 1000L) + 5L)
        }
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        val cal = PersianUtils.newCalendar(TimeZone.getDefault(), now)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)

        val text = PersianUtils.two(hour) + ":" + PersianUtils.two(minute)
        val spannable = SpannableString(text)
        if (second % 2 == 1) {
            // Blink the colon without changing the text width.
            spannable.setSpan(ForegroundColorSpan(Color.TRANSPARENT), 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        timeView.text = spannable
        dots.setSecond(second)

        val minuteKey = hour * 60 + minute
        if (minuteKey != lastMinuteKey) {
            lastMinuteKey = minuteKey
            greetingView.text = PersianUtils.greeting(hour)
            dateView.text = PersianUtils.dateText(cal)
            drift()
        }
        updateAlarmLine(now)
    }

    private fun updateAlarmLine(now: Long) {
        if (!::alarmView.isInitialized) return
        val next = latestAlarms.filter { it.enabled }
            .map { it to AlarmScheduler.nextTriggerTimeMillis(it) }
            .minByOrNull { it.second }
        alarmView.text = if (next == null) {
            "هشدار فعالی نداری"
        } else {
            val remainingMinutes = ((next.second - now).coerceAtLeast(0L) + 59_999L) / 60_000L
            "⏰ " + PersianUtils.two(next.first.hour) + ":" + PersianUtils.two(next.first.minute) +
                "  •  " + PersianUtils.hoursAndMinutes(remainingMinutes) + " دیگر"
        }
    }

    /** Tiny random shift each minute so no pixel stays lit in the same place all night. */
    private fun drift() {
        val range = dp(14)
        content.animate()
            .translationX(Random.nextInt(-range, range + 1).toFloat())
            .translationY(Random.nextInt(-range, range + 1).toFloat())
            .setDuration(2500L)
            .start()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun dp(value: Int): Int = UiFx.dp(this, value)

    /** 60 dots, one per second of the current minute. */
    private class SecondsDotsView(context: Context) : View(context) {
        var accent: Int = Color.WHITE
            set(value) {
                field = value
                invalidate()
            }
        private var second = 0
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val density = resources.displayMetrics.density

        fun setSecond(value: Int) {
            if (value != second) {
                second = value
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val pad = 4 * density
            val usable = width - 2 * pad
            if (usable <= 0f) return
            val step = usable / 59f
            val radius = min(step * 0.3f, 3f * density)
            val cy = height / 2f
            for (i in 0 until 60) {
                val current = i == second
                paint.color = accent
                paint.alpha = if (i <= second) 255 else 45
                // Right-to-left, like every other progress indicator in a Persian UI.
                val cx = width - pad - i * step
                canvas.drawCircle(cx, cy, if (current) radius * 1.6f else radius, paint)
            }
        }
    }
}
