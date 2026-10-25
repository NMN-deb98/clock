package com.example.alarmclock

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Sentences for the "type a motivational sentence" wake-up method (no punctuation on purpose). */
object MotivationalSentences {
    val all = listOf(
        "امروز را با انرژی شروع کن و بهترین نسخه خودت باش",
        "هر صبح یک فرصت تازه است برای ساختن آینده",
        "آرزوها با خوابیدن به دست نمی‌آیند پس بلند شو",
        "یک قدم کوچک امروز تو را به هدف بزرگ نزدیک می‌کند",
        "تو از چیزی که فکر می‌کنی قوی‌تر هستی",
        "پیروزی از همین لحظه که بیدار می‌شوی شروع می‌شود",
        "امروز بهترین روز برای شروع دوباره است",
        "با لبخند بیدار شو که دنیا منتظر توست",
        "تلاش امروز تو سرمایه فردای توست",
        "تنبلی را کنار بگذار و به سمت رویایت حرکت کن",
        "هر روز یک شروع تازه با امید بیشتر است",
        "نظم و انرژی صبحگاهی کلید موفقیت است",
        "بیدار شو و نگذار فرصت‌های امروز از دستت بروند",
        "ساعت‌های صبح گران‌بهاترین ساعت‌های روز هستند",
        "برای ساختن فردایی بهتر باید از امروز بلند شوی",
        "باور داشته باش که امروز کارهای بزرگی انجام می‌دهی",
        "صبح بخیر قهرمان وقت حرکت رسیده است"
    )
}

/** Base of every wake-up challenge: a full-screen view that calls [onSolved] exactly once. */
abstract class ChallengeView(context: Context) : FrameLayout(context) {
    var onSolved: (() -> Unit)? = null
    private var finished = false

    protected val cyan = context.getColor(R.color.accent_cyan)
    protected val violet = context.getColor(R.color.accent_violet)
    protected val primary = context.getColor(R.color.text_primary)
    protected val secondary = context.getColor(R.color.text_secondary)
    protected val surface = context.getColor(R.color.surface_primary)
    protected val elevated = context.getColor(R.color.surface_elevated)
    protected val border = context.getColor(R.color.surface_border)
    protected val danger = context.getColor(R.color.danger)
    protected val bgColor = context.getColor(R.color.app_background)

    protected val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(22), dp(44), dp(22), dp(24))
    }

    init {
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    open fun start() {}
    open fun stop() {}

    protected fun solved() {
        if (finished) return
        finished = true
        stop()
        onSolved?.invoke()
    }

    protected fun dp(value: Int): Int = UiFx.dp(context, value)

    protected fun label(text: String, size: Float, color: Int = primary, bold: Boolean = false): TextView =
        TextView(context).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    protected fun addTitle(title: String, subtitle: String) {
        content.addView(label(title, 24f, primary, true))
        content.addView(
            label(subtitle, 14f, secondary).apply { setPadding(0, dp(6), 0, dp(14)) },
            LinearLayout.LayoutParams(-1, -2)
        )
    }

    protected fun progressBar(max: Int): ProgressBar =
        ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            this.max = max
            progress = 0
            progressTintList = ColorStateList.valueOf(cyan)
            progressBackgroundTintList = ColorStateList.valueOf(border)
        }

    @Suppress("DEPRECATION")
    protected fun buzz(ms: Long = 70L) {
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }
}

// ----------------------------------------------------------------------------- Walking

class WalkChallengeView(context: Context, private val target: Int) : ChallengeView(context), SensorEventListener {
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val counter = label(PersianUtils.digits(0), 72f, cyan, true)
    private val bar = progressBar(target)
    private val sensorNote = label("", 12f, secondary)
    private var steps = 0
    private var lastStepAt = 0L
    private var smoothed = SensorManager.GRAVITY_EARTH

    init {
        addTitle("راه برو تا زنگ خاموش شود", "گوشی را همراه خودت ببر و ${PersianUtils.digits(target)} قدم بردار")
        content.addView(counter, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(30) })
        content.addView(label("از ${PersianUtils.digits(target)} قدم", 16f, secondary))
        content.addView(bar, LinearLayout.LayoutParams(-1, dp(14)).apply { topMargin = dp(26) })
        content.addView(sensorNote, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })
    }

    override fun start() {
        val stepSensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        when {
            stepSensor != null && allowed -> {
                sensors.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
                sensorNote.text = "قدم‌ها با حسگر قدم‌شمار گوشی شمرده می‌شوند"
            }
            accelerometer != null -> {
                sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
                sensorNote.text = "قدم‌ها با تشخیص حرکت گوشی شمرده می‌شوند"
            }
            else -> {
                sensorNote.text = "این گوشی حسگر حرکت ندارد؛ برای هر قدم روی دکمه بزن"
                content.addView(
                    label("ثبت یک قدم", 16f, bgColor, true).apply {
                        background = UiFx.rounded(context, cyan, 20)
                        setPadding(dp(24), dp(14), dp(24), dp(14))
                        setOnClickListener { addStep() }
                    },
                    LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(18) }
                )
            }
        }
    }

    override fun stop() {
        sensors.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            addStep()
            return
        }
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        smoothed = smoothed * 0.9f + magnitude * 0.1f
        val now = SystemClock.elapsedRealtime()
        if (magnitude - smoothed > 1.7f && now - lastStepAt > 350L) {
            lastStepAt = now
            addStep()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun addStep() {
        steps++
        counter.text = PersianUtils.digits(steps)
        bar.progress = steps
        if (steps >= target) solved()
    }
}

// ----------------------------------------------------------------------------- Shaking

class ShakeChallengeView(context: Context, level: String) : ChallengeView(context), SensorEventListener {
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val needed: Int
    private val threshold: Float
    private val gapMs: Long
    private val levelName: String
    private val counter: TextView
    private val bar: ProgressBar
    private var shakes = 0
    private var lastShakeAt = 0L

    init {
        when (level) {
            AlarmOptions.SHAKE_EXTREME -> { needed = 50; threshold = 27f; gapMs = 200L; levelName = "خیلی سخت" }
            AlarmOptions.SHAKE_HARD -> { needed = 30; threshold = 20f; gapMs = 220L; levelName = "سخت" }
            else -> { needed = 15; threshold = 14f; gapMs = 260L; levelName = "آسان" }
        }
        addTitle("گوشی را تکان بده", "درجهٔ سختی: $levelName  •  ${PersianUtils.digits(needed)} تکان قوی")
        counter = label(PersianUtils.digits(0), 72f, cyan, true)
        content.addView(counter, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(30) })
        content.addView(label("از ${PersianUtils.digits(needed)} تکان", 16f, secondary))
        bar = progressBar(needed)
        content.addView(bar, LinearLayout.LayoutParams(-1, dp(14)).apply { topMargin = dp(26) })
        content.addView(
            label("گوشی را محکم در دست بگیر و تند تکان بده", 12f, secondary),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) }
        )
    }

    override fun start() {
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        } else {
            content.addView(
                label("این گوشی شتاب‌سنج ندارد؛ برای هر تکان روی دکمه بزن", 13f, danger),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
            )
            content.addView(
                label("ثبت یک تکان", 16f, bgColor, true).apply {
                    background = UiFx.rounded(context, cyan, 20)
                    setPadding(dp(24), dp(14), dp(24), dp(14))
                    setOnClickListener { addShake() }
                },
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) }
            )
        }
    }

    override fun stop() {
        sensors.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val now = SystemClock.elapsedRealtime()
        if (magnitude > threshold && now - lastShakeAt > gapMs) {
            lastShakeAt = now
            addShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun addShake() {
        shakes++
        counter.text = PersianUtils.digits(shakes)
        bar.progress = shakes
        if (shakes >= needed) solved()
    }
}

// ----------------------------------------------------------------------------- Typing

class TypingChallengeView(
    context: Context,
    private val mode: String,
    private val custom: String
) : ChallengeView(context) {
    private val targetView = label("", 22f, cyan, true)
    private val input = EditText(context)
    private val status = label("", 13f, secondary)
    private var target = ""

    init {
        val scroll = ScrollView(context).apply { isFillViewport = false }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scroll.addView(column, FrameLayout.LayoutParams(-1, -2))
        content.addView(scroll, LinearLayout.LayoutParams(-1, -1))

        val (title, subtitle) = when (mode) {
            AlarmOptions.TYPING_RANDOM_EN -> "حروف را تایپ کن" to "این حروف و اعداد انگلیسی را دقیقاً همین‌طور بنویس"
            AlarmOptions.TYPING_RANDOM_FA -> "حروف را تایپ کن" to "این حروف فارسی را دقیقاً همین‌طور بنویس"
            AlarmOptions.TYPING_CUSTOM -> "جملهٔ خودت را تایپ کن" to "همان جمله‌ای که خودت برای بیدار شدن انتخاب کردی"
            else -> "جملهٔ انگیزشی را تایپ کن" to "این جمله را بدون تغییر بنویس تا زنگ خاموش شود"
        }
        column.addView(label(title, 24f, primary, true))
        column.addView(
            label(subtitle, 14f, secondary).apply { setPadding(0, dp(6), 0, dp(16)) },
            LinearLayout.LayoutParams(-1, -2)
        )
        targetView.apply {
            setPadding(dp(16), dp(20), dp(16), dp(20))
            background = UiFx.rounded(context, surface, 20, border)
            setTextIsSelectable(false)
            isLongClickable = false
            if (mode == AlarmOptions.TYPING_RANDOM_EN) textDirection = View.TEXT_DIRECTION_LTR
        }
        column.addView(targetView, LinearLayout.LayoutParams(-1, -2))

        input.apply {
            hint = "اینجا تایپ کن"
            setHintTextColor(secondary)
            setTextColor(primary)
            textSize = 20f
            gravity = Gravity.CENTER
            setSingleLine(true)
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = UiFx.rounded(context, elevated, 16, border)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            if (mode == AlarmOptions.TYPING_RANDOM_EN) textDirection = View.TEXT_DIRECTION_LTR
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = check()
            })
        }
        column.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        column.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        if (mode != AlarmOptions.TYPING_CUSTOM) {
            column.addView(
                label("↻  متن دیگر", 14f, violet, true).apply {
                    setPadding(dp(18), dp(12), dp(18), dp(12))
                    setOnClickListener { newTarget() }
                },
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) }
            )
        }
        newTarget()
    }

    override fun start() {
        input.requestFocus()
        postDelayed({
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 350L)
    }

    override fun stop() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun newTarget() {
        target = when (mode) {
            AlarmOptions.TYPING_RANDOM_EN -> randomLetters("ABCDEFGHJKMNPQRSTUVWXYZ23456789", 6)
            AlarmOptions.TYPING_RANDOM_FA -> randomLetters("ابپتجچحخدرزسشصطعفقکگلمنوهی", 6)
            AlarmOptions.TYPING_CUSTOM -> custom.trim()
            else -> MotivationalSentences.all.random()
        }
        targetView.text = if (mode == AlarmOptions.TYPING_RANDOM_EN || mode == AlarmOptions.TYPING_RANDOM_FA)
            target.toCharArray().joinToString("   ") else target
        input.setText("")
        status.text = ""
    }

    private fun randomLetters(pool: String, count: Int): String =
        String(CharArray(count) { pool[Random.nextInt(pool.length)] })

    private fun check() {
        val typed = normalize(input.text?.toString().orEmpty())
        val wanted = normalize(target)
        if (wanted.isEmpty()) return
        if (typed == wanted) {
            status.text = "آفرین!"
            status.setTextColor(cyan)
            solved()
        } else if (typed.length >= wanted.length) {
            status.text = "هنوز درست نیست؛ با دقت مقایسه کن"
            status.setTextColor(danger)
        } else {
            status.text = ""
        }
    }

    private fun normalize(value: String): String {
        val out = StringBuilder(value.length)
        for (ch in value) {
            when {
                ch.isWhitespace() || ch == '‌' || ch == '‏' || ch == '‎' || ch == 'ـ' -> Unit
                ch == 'ي' -> out.append('ی')
                ch == 'ك' -> out.append('ک')
                ch in '۰'..'۹' -> out.append('0' + (ch - '۰'))
                ch in '٠'..'٩' -> out.append('0' + (ch - '٠'))
                else -> out.append(ch.lowercaseChar())
            }
        }
        return out.toString()
    }
}

// ----------------------------------------------------------------------------- Tapping

class TapChallengeView(context: Context, private val target: Int) : ChallengeView(context) {
    private val counter = label(PersianUtils.digits(0), 56f, cyan, true)
    private val bar = progressBar(target)
    private val arena = FrameLayout(context)
    private val button = TextView(context)
    private var taps = 0

    init {
        addTitle("روی دایره بزن", "هر بار که می‌زنی جابه‌جا می‌شود  •  ${PersianUtils.digits(target)} بار")
        content.addView(counter)
        content.addView(bar, LinearLayout.LayoutParams(-1, dp(12)).apply { topMargin = dp(10) })
        content.addView(arena, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) })
        button.apply {
            text = "لمس کن"
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(bgColor)
            setTypeface(typeface, Typeface.BOLD)
            this.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(cyan)
            }
            elevation = dp(6).toFloat()
            setOnClickListener { onTap() }
        }
        arena.addView(button, FrameLayout.LayoutParams(dp(120), dp(120)))
    }

    override fun start() {
        arena.post { place(false) }
    }

    private fun onTap() {
        taps++
        counter.text = PersianUtils.digits(taps)
        bar.progress = taps
        buzz(25L)
        if (taps >= target) solved() else place(true)
    }

    private fun place(animate: Boolean) {
        val maxX = (arena.width - dp(120)).coerceAtLeast(0)
        val maxY = (arena.height - dp(120)).coerceAtLeast(0)
        val x = Random.nextInt(maxX + 1).toFloat()
        val y = Random.nextInt(maxY + 1).toFloat()
        if (animate) button.animate().x(x).y(y).setDuration(140L).start() else {
            button.x = x
            button.y = y
        }
    }
}

// ----------------------------------------------------------------------------- Pattern

/** Draws a size x size dot grid and reports the dots the finger passed over, in order. */
class PatternLockView(context: Context, private val size: Int) : View(context) {
    var listener: ((List<Int>) -> Unit)? = null
    var inputEnabled = true

    private val picked = ArrayList<Int>()
    private var fingerX = 0f
    private var fingerY = 0f
    private var dragging = false
    private var error = false
    private val ui = Handler(Looper.getMainLooper())

    private val idleColor = context.getColor(R.color.surface_border)
    private val activeColor = context.getColor(R.color.accent_cyan)
    private val errorColor = context.getColor(R.color.danger)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 6f * resources.displayMetrics.density
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val side = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED || h == 0) w else min(w, h)
        setMeasuredDimension(side, side)
    }

    fun reset() {
        ui.removeCallbacksAndMessages(null)
        picked.clear()
        error = false
        dragging = false
        invalidate()
    }

    fun showError() {
        error = true
        invalidate()
        ui.postDelayed({ reset() }, 700L)
    }

    private fun cell(): Float = width / size.toFloat()
    private fun centerX(i: Int): Float = (i % size + 0.5f) * cell()
    private fun centerY(i: Int): Float = (i / size + 0.5f) * cell()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inputEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ui.removeCallbacksAndMessages(null)
                picked.clear()
                error = false
                dragging = true
                fingerX = event.x
                fingerY = event.y
                hit(event.x, event.y)
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                fingerX = event.x
                fingerY = event.y
                hit(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
                invalidate()
                if (picked.isNotEmpty()) listener?.invoke(picked.toList())
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                picked.clear()
                invalidate()
            }
        }
        return true
    }

    private fun hit(x: Float, y: Float) {
        val radius = cell() * 0.33f
        for (i in 0 until size * size) {
            if (picked.contains(i)) continue
            val dx = x - centerX(i)
            val dy = y - centerY(i)
            if (dx * dx + dy * dy <= radius * radius) {
                picked.add(i)
                break
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val color = if (error) errorColor else activeColor
        linePaint.color = color
        for (i in 1 until picked.size) {
            canvas.drawLine(centerX(picked[i - 1]), centerY(picked[i - 1]), centerX(picked[i]), centerY(picked[i]), linePaint)
        }
        if (dragging && picked.isNotEmpty()) {
            val last = picked.last()
            canvas.drawLine(centerX(last), centerY(last), fingerX, fingerY, linePaint)
        }
        val dotRadius = min(cell() * 0.1f, 11f * resources.displayMetrics.density)
        for (i in 0 until size * size) {
            val active = picked.contains(i)
            if (active) {
                haloPaint.color = color
                haloPaint.alpha = 70
                canvas.drawCircle(centerX(i), centerY(i), dotRadius * 2.6f, haloPaint)
            }
            dotPaint.color = if (active) color else idleColor
            canvas.drawCircle(centerX(i), centerY(i), if (active) dotRadius * 1.3f else dotRadius, dotPaint)
        }
    }
}

class PatternChallengeView(
    context: Context,
    private val size: Int,
    private val expected: List<Int>
) : ChallengeView(context) {
    private val pad = PatternLockView(context, size)
    private val status = label("", 14f, secondary)

    init {
        addTitle("الگو را بکش", "همان الگویی که هنگام تنظیم هشدار ساختی  •  ${PersianUtils.digits(size)}×${PersianUtils.digits(size)}")
        content.addView(pad, LinearLayout.LayoutParams(-1, 0, 1f).apply { gravity = Gravity.CENTER_HORIZONTAL })
        content.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        pad.listener = { drawn ->
            if (drawn == expected) {
                pad.inputEnabled = false
                status.text = "درست بود!"
                status.setTextColor(cyan)
                solved()
            } else {
                pad.showError()
                status.text = "الگو درست نیست؛ دوباره تلاش کن"
                status.setTextColor(danger)
                buzz(120L)
            }
        }
    }

    override fun start() {
        // A missing pattern must never lock the user out of their own alarm.
        if (expected.size < 2) solved()
    }

    override fun stop() {
        pad.reset()
    }
}

// ----------------------------------------------------------------------------- Memory game

class MemoryChallengeView(context: Context, private val rounds: Int) : ChallengeView(context) {
    private val ui = Handler(Looper.getMainLooper())
    private val roundText = label("", 16f, secondary)
    private val status = label("", 18f, primary, true)
    private val pads = ArrayList<View>()
    private var round = 1
    private var sequence = IntArray(0)
    private var position = 0
    private var inputEnabled = false

    init {
        addTitle("بازی حافظه", "ترتیب روشن شدن رنگ‌ها را ببین و همان را تکرار کن")
        content.addView(roundText)
        content.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(14) })
        val colors = intArrayOf(
            cyan, violet,
            context.getColor(R.color.accent_amber), context.getColor(R.color.accent_rose)
        )
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        for (row in 0 until 2) {
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0 until 2) {
                val index = row * 2 + col
                val pad = View(context).apply {
                    background = UiFx.rounded(context, colors[index], 26)
                    alpha = DIM
                    setOnClickListener { onPad(index) }
                }
                pads.add(pad)
                line.addView(pad, LinearLayout.LayoutParams(0, -1, 1f).apply {
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                })
            }
            grid.addView(line, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        content.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    override fun start() {
        ui.postDelayed({ playRound() }, 700L)
    }

    override fun stop() {
        ui.removeCallbacksAndMessages(null)
    }

    private fun flash(index: Int, durationMs: Long) {
        pads[index].alpha = 1f
        ui.postDelayed({ pads[index].alpha = DIM }, durationMs)
    }

    private fun playRound() {
        inputEnabled = false
        position = 0
        val length = round + 2
        sequence = IntArray(length) { Random.nextInt(4) }
        roundText.text = "مرحلهٔ ${PersianUtils.digits(round)} از ${PersianUtils.digits(rounds)}"
        status.text = "خوب نگاه کن…"
        var delay = 500L
        for (i in 0 until length) {
            val color = sequence[i]
            ui.postDelayed({ flash(color, 420L) }, delay)
            delay += 700L
        }
        ui.postDelayed({
            inputEnabled = true
            status.text = "حالا نوبت توست"
        }, delay)
    }

    private fun onPad(index: Int) {
        if (!inputEnabled) return
        flash(index, 180L)
        if (index == sequence[position]) {
            position++
            if (position == sequence.size) {
                inputEnabled = false
                if (round >= rounds) {
                    status.text = "آفرین!"
                    solved()
                } else {
                    round++
                    status.text = "عالی! مرحلهٔ بعد"
                    ui.postDelayed({ playRound() }, 900L)
                }
            }
        } else {
            inputEnabled = false
            status.text = "اشتباه بود؛ دوباره از اول"
            buzz(120L)
            ui.postDelayed({ playRound() }, 1100L)
        }
    }

    private companion object {
        const val DIM = 0.32f
    }
}

// ----------------------------------------------------------------------------- Number order

class OrderChallengeView(context: Context, private val count: Int) : ChallengeView(context) {
    private val ui = Handler(Looper.getMainLooper())
    private val progress = label("", 18f, cyan, true)
    private val cells = HashMap<Int, TextView>()
    private var next = 1

    init {
        addTitle("اعداد را به ترتیب بزن", "از ${PersianUtils.digits(1)} شروع کن تا ${PersianUtils.digits(count)}")
        content.addView(progress, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val columns = ceil(sqrt(count.toFloat())).toInt()
        val numbers = (1..count).shuffled()
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        var index = 0
        while (index < numbers.size) {
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0 until columns) {
                val lp = LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
                if (index < numbers.size) {
                    val n = numbers[index++]
                    val cell = TextView(context).apply {
                        text = PersianUtils.digits(n)
                        gravity = Gravity.CENTER
                        textSize = 22f
                        setTextColor(primary)
                        setTypeface(typeface, Typeface.BOLD)
                        background = UiFx.rounded(context, elevated, 16, border)
                        setOnClickListener { onCell(n) }
                    }
                    cells[n] = cell
                    line.addView(cell, lp)
                } else {
                    line.addView(View(context), lp)
                }
            }
            grid.addView(line, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        content.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        updateProgress()
    }

    override fun stop() {
        ui.removeCallbacksAndMessages(null)
    }

    private fun updateProgress() {
        progress.text = if (next <= count) "عدد بعدی: ${PersianUtils.digits(next)}" else "تمام شد!"
    }

    private fun onCell(n: Int) {
        val cell = cells[n] ?: return
        if (n == next) {
            cell.alpha = 0.22f
            cell.isEnabled = false
            next++
            updateProgress()
            if (next > count) solved()
        } else if (n > next) {
            buzz(90L)
            cell.background = UiFx.rounded(context, danger, 16)
            ui.postDelayed({ cell.background = UiFx.rounded(context, elevated, 16, border) }, 260L)
        }
    }
}

/** Draw any pattern that links at least [minNodes] dots to turn the alarm off. */
class FreePatternChallengeView(context: Context, private val size: Int) : ChallengeView(context) {
    private val pad = PatternLockView(context, size)
    private val status = label("", 14f, secondary)
    private val minNodes = 4

    init {
        addTitle("الگو را بکش", "با کشیدن انگشت، حداقل ${PersianUtils.digits(minNodes)} نقطه را به هم وصل کن")
        content.addView(pad, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        pad.listener = { drawn ->
            if (drawn.size >= minNodes) {
                pad.inputEnabled = false
                status.text = "آفرین!"
                status.setTextColor(cyan)
                solved()
            } else {
                pad.showError()
                status.text = "حداقل ${PersianUtils.digits(minNodes)} نقطه را وصل کن"
                status.setTextColor(danger)
            }
        }
    }

    override fun stop() {
        pad.reset()
    }
}
