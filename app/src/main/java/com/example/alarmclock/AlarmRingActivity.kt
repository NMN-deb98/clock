package com.example.alarmclock

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.WindowManager
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.alarmclock.databinding.ActivityAlarmRingBinding
import kotlin.random.Random

class AlarmRingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAlarmRingBinding
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var iconPulse: android.animation.ObjectAnimator? = null
    private val pulseAnimators = mutableListOf<android.animation.Animator>()
    private val tickHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            tickHandler.postDelayed(this, 1000L)
        }
    }
    private var alarmId: Int = -1
    private var deleteAfterRinging: Boolean = false
    private var wakeMode: String = "NONE"
    private var mathDifficulty: String = "MEDIUM"
    private var walkTarget: Int = 40
    private var shakeDifficulty: String = "MEDIUM"
    private var typeMode: String = "RANDOM"
    private var customPhrase: String = ""
    private var touchTarget: Int = 30
    private var patternSize: Int = 3
    private var challengeDialog: AlertDialog? = null
    private var challengeProgressView: TextView? = null
    private var stepBaseline: Float? = null
    private var completedSteps = 0
    private var shakeCount = 0
    private var lastShakeAt = 0L
    private var sensorManager: SensorManager? = null
    private var activeSensor: Sensor? = null
    private var activeSensorListener: SensorEventListener? = null
    private val challengeHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var holdStartedAt = 0L
    private var holdRunnable: Runnable? = null
    private var mathQuestions: List<MathQuestion> = emptyList()
    private var mathIndex = 0
    private var photoIndex = 0
    private val photoPrompts = listOf("یک کتاب", "یک لیوان", "یک کفش", "یک صندلی", "یک کلید", "یک گیاه", "یک کیف", "یک قاشق", "یک بالش", "یک ساعت")
    private val usedPhotoPrompts = mutableSetOf<String>()
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap == null) {
            AlertDialog.Builder(this).setTitle("عکس ثبت نشد")
                .setMessage("برای ادامه باید از سوژه خواسته‌شده عکس بگیرید.")
                .setPositiveButton("تلاش دوباره") { _, _ -> capturePhoto() }
                .setNegativeButton("لغو", null).show()
            return@registerForActivityResult
        }
        confirmPhoto(bitmap)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure the lock-screen window before the content is attached.
        // This is important when launched from a full-screen alarm notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        binding = ActivityAlarmRingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        setShowWhenLockedAndTurnScreenOn()
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        alarmId = intent.getIntExtra("alarm_id", -1)
        val label = intent.getStringExtra("label") ?: ""
        val ringtoneUriString = intent.getStringExtra("ringtone_uri") ?: ""
        val shouldVibrate = intent.getBooleanExtra("vibrate", true)
        wakeMode = intent.getStringExtra("wake_mode") ?: "NONE"
        mathDifficulty = intent.getStringExtra("math_difficulty") ?: "MEDIUM"
        deleteAfterRinging = intent.getBooleanExtra("delete_after_ringing", false)
        walkTarget = intent.getIntExtra("walk_target", 40).coerceIn(1, 10000)
        shakeDifficulty = intent.getStringExtra("shake_difficulty") ?: "MEDIUM"
        typeMode = intent.getStringExtra("type_mode") ?: "RANDOM"
        customPhrase = intent.getStringExtra("custom_phrase").orEmpty()
        touchTarget = intent.getIntExtra("touch_target", 30).coerceIn(1, 10000)
        patternSize = intent.getIntExtra("pattern_size", 3).coerceIn(3, 5)
        binding.textNote.text = label.ifBlank { getString(R.string.no_note) }

        // AlarmRingingService owns playback so sound survives if this Activity cannot open over lock screen.

        if (wakeMode == "MATH") {
            mathQuestions = createQuestions(mathDifficulty)
            binding.textDifficulty.visibility = View.VISIBLE
            binding.textDifficulty.text = "چالش ریاضی • ${difficultyLabel(mathDifficulty)}"
            binding.buttonDismiss.text = "شروع چالش"
        } else if (wakeMode == "PHOTO") {
            binding.buttonDismiss.text = "شروع چالش عکس"
        } else if (wakeMode != "NONE") {
            binding.buttonDismiss.text = "شروع چالش بیداری"
        }
        binding.buttonDismiss.setOnClickListener {
            when (wakeMode) {
                "MATH" -> showMathQuestion()
                "PHOTO" -> capturePhoto()
                "WALK" -> startWalkChallenge()
                "SHAKE" -> startShakeChallenge()
                "TYPE" -> startTypingChallenge()
                "TOUCH" -> startTouchChallenge()
                "PATTERN" -> startPatternChallenge()
                "MEMORY" -> startMemoryChallenge()
                "HOLD" -> startHoldChallenge()
                else -> stopAndFinish()
            }
        }
        binding.buttonSnooze.setOnClickListener { snoozeAndFinish() }
        applyFonts()
        iconPulse = UiFx.breathe(binding.ringIcon, 1.12f, 650L)
        startPulses()
        updateClock()
    }

    private fun applyFonts() {
        binding.textTime.typeface = UiFx.font(this, 300)
        binding.textDate.typeface = UiFx.font(this, 400)
        binding.textTitle.typeface = UiFx.font(this, 500)
        binding.textNote.typeface = UiFx.font(this, 500)
        binding.textDifficulty.typeface = UiFx.font(this, 700)
        binding.buttonDismiss.typeface = UiFx.font(this, 700)
        binding.buttonSnooze.typeface = UiFx.font(this, 500)
    }

    private fun updateClock() {
        val now = System.currentTimeMillis()
        val cal = PersianUtils.newCalendar(java.util.TimeZone.getDefault(), now)
        binding.textTime.text = PersianUtils.two(cal.get(java.util.Calendar.HOUR_OF_DAY)) + ":" +
            PersianUtils.two(cal.get(java.util.Calendar.MINUTE))
        binding.textDate.text = PersianUtils.dateText(cal)
    }

    private fun startPulses() {
        listOf(binding.pulse1, binding.pulse2, binding.pulse3).forEachIndexed { i, v ->
            val a = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                v,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f, 2.1f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f, 2.1f),
                android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0.9f, 0f)
            )
            a.duration = 2400L
            a.startDelay = i * 800L
            a.repeatCount = android.animation.ObjectAnimator.INFINITE
            a.interpolator = android.view.animation.DecelerateInterpolator()
            a.start()
            pulseAnimators.add(a)
        }
    }

    private val closeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) { finish() }
    }

    override fun onStart() {
        super.onStart()
        val filter = android.content.IntentFilter(AlarmActionReceiver.ACTION_CLOSE_RING)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(closeReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(closeReceiver, filter)
        }
    }

    override fun onStop() {
        runCatching { unregisterReceiver(closeReceiver) }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        tickHandler.removeCallbacks(tick)
        tickHandler.post(tick)
    }

    override fun onPause() {
        tickHandler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) setIntent(intent)
    }

    private fun setShowWhenLockedAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun startRinging(uriString: String) {
        if (uriString == "silent") return
        val uri: Uri = if (uriString.isNotBlank()) Uri.parse(uriString)
        else RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            play()
        }
    }

    private fun startVibrating() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 800, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun difficultyLabel(difficulty: String): String = when (difficulty) {
        "EASY" -> "آسان"
        "HARD" -> "سخت"
        else -> "متوسط"
    }

    private data class MathQuestion(val prompt: String, val answer: Int)

    private fun createQuestions(difficulty: String): List<MathQuestion> {
        val questions = linkedMapOf<String, Int>()
        while (questions.size < 3) {
            val a: Int
            val b: Int
            val expression: String
            val answer: Int
            when (difficulty) {
                "EASY" -> {
                    a = Random.nextInt(2, 21); b = Random.nextInt(1, 11)
                    if (Random.nextBoolean()) { expression = "$a + $b"; answer = a + b }
                    else { val high = maxOf(a, b); val low = minOf(a, b); expression = "$high − $low"; answer = high - low }
                }
                "HARD" -> {
                    a = Random.nextInt(12, 100); b = Random.nextInt(3, 25)
                    when (Random.nextInt(3)) {
                        0 -> { expression = "$a × $b"; answer = a * b }
                        1 -> { val high = maxOf(a, b); val low = minOf(a, b); expression = "$high − $low"; answer = high - low }
                        else -> { val divisor = Random.nextInt(2, 13); val quotient = Random.nextInt(4, 20); expression = "${divisor * quotient} ÷ $divisor"; answer = quotient }
                    }
                }
                else -> {
                    a = Random.nextInt(8, 51); b = Random.nextInt(3, 21)
                    when (Random.nextInt(3)) {
                        0 -> { expression = "$a × $b"; answer = a * b }
                        1 -> { expression = "$a + $b"; answer = a + b }
                        else -> { val high = maxOf(a, b); val low = minOf(a, b); expression = "$high − $low"; answer = high - low }
                    }
                }
            }
            questions.putIfAbsent(expression, answer)
        }
        return questions.map { MathQuestion(it.key, it.value) }.shuffled()
    }

    private fun toPersianDigits(value: String): String = value.map { ch ->
        if (ch in '0'..'9') ('۰'.code + (ch - '0')).toChar() else ch
    }.joinToString("")

    private fun showMathQuestion() {
        if (mathIndex >= mathQuestions.size) {
            stopAndFinish()
            return
        }
        val question = mathQuestions[mathIndex]
        val input = EditText(this).apply {
            hint = "پاسخ را وارد کن"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setSingleLine(true)
            textSize = 20f
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
            setPadding(18, 14, 18, 14)
            background = androidx.core.content.ContextCompat.getDrawable(this@AlarmRingActivity, R.drawable.bg_input_field)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 10, 24, 4)
            addView(TextView(this@AlarmRingActivity).apply {
                text = "سؤال ${mathIndex + 1} از ۳"
                textSize = 12f
                letterSpacing = 0.12f
                setTextColor(getColor(R.color.accent_cyan))
            })
            addView(TextView(this@AlarmRingActivity).apply {
                text = "${toPersianDigits(question.prompt)} = ؟"
                // Render the complete expression LTR; Persian digits retain their intended visual order.
                textDirection = View.TEXT_DIRECTION_LTR
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                textSize = 34f
                gravity = android.view.Gravity.CENTER
                setTextColor(getColor(R.color.text_primary))
                setPadding(0, 22, 0, 22)
            })
            addView(TextView(this@AlarmRingActivity).apply {
                text = "درجه سختی: ${difficultyLabel(mathDifficulty)}"
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setTextColor(getColor(R.color.accent_violet))
                setPadding(0, 0, 0, 14)
            })
            addView(input, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("برای خاموش کردن هشدار، مسئله را حل کن")
            .setView(content)
            .setCancelable(false)
            .setNegativeButton("بررسی پاسخ", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_surface)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(getColor(R.color.accent_cyan))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                val answer = input.text.toString().trim().toIntOrNull()
                if (answer == question.answer) {
                    mathIndex++
                    dialog.dismiss()
                    if (mathIndex == 3) stopAndFinish() else showMathQuestion()
                } else {
                    input.error = "پاسخ درست نیست؛ دوباره تلاش کن."
                    input.text?.clear()
                }
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_surface)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(getColor(R.color.accent_cyan))
    }

    private fun capturePhoto() {
        if (photoIndex >= 2) { stopAndFinish(); return }
        val prompt = photoPrompts.filterNot { it in usedPhotoPrompts }.random()
        usedPhotoPrompts.add(prompt)
        currentPhotoPrompt = prompt
        AlertDialog.Builder(this)
            .setTitle("چالش عکس ${photoIndex + 1} از ۲")
            .setMessage("از این وسیله عکس بگیر: $prompt")
            .setPositiveButton("باز کردن دوربین") { _, _ -> cameraLauncher.launch(null) }
            .setNegativeButton("بازگشت به چالش", null)
            .setCancelable(false)
            .show()
    }

    private var currentPhotoPrompt = ""
    private fun confirmPhoto(bitmap: Bitmap) {
        val image = ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            maxHeight = 520
        }
        AlertDialog.Builder(this)
            .setTitle("تأیید عکس")
            .setMessage("آیا عکس ${currentPhotoPrompt} واضح است و سوژه در آن دیده می‌شود؟")
            .setView(image)
            .setPositiveButton("تأیید عکس") { _, _ ->
                photoIndex++
                if (photoIndex >= 2) stopAndFinish() else capturePhoto()
            }
            .setNegativeButton("دوباره عکس بگیر") { _, _ -> capturePhoto() }
            .setCancelable(false)
            .show()
    }

    private fun showProgressChallenge(title: String, initialText: String): AlertDialog {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 16, 28, 12)
            addView(TextView(this@AlarmRingActivity).apply {
                text = initialText
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                setTextColor(getColor(R.color.text_primary))
                challengeProgressView = this
            })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(content)
            .setCancelable(false)
            .setNegativeButton("ادامه چالش", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_surface)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(getColor(R.color.accent_cyan))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { /* Keep progress visible while the sensor challenge continues. */ }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_surface)
        challengeDialog = dialog
        return dialog
    }

    private fun stopChallengeSensor() {
        activeSensorListener?.let { listener -> sensorManager?.unregisterListener(listener) }
        activeSensorListener = null
        activeSensor = null
        sensorManager = null
        challengeHandler.removeCallbacksAndMessages(null)
    }

    private fun startWalkChallenge() {
        val manager = getSystemService(SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) {
            AlertDialog.Builder(this).setTitle("شمارش قدم پشتیبانی نمی‌شود")
                .setMessage("حسگر شمارش قدم در این گوشی در دسترس نیست. از روش تکان دادن یا لمس صفحه استفاده کن.")
                .setPositiveButton("متوجه شدم", null).show()
            return
        }
        stepBaseline = null
        completedSteps = 0
        showProgressChallenge("راه برو", "۰ از " + PersianUtils.digits(walkTarget) + " قدم")
        sensorManager = manager
        activeSensor = sensor
        activeSensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val current = event.values.firstOrNull() ?: return
                val baseline = stepBaseline
                if (baseline == null) stepBaseline = current
                else completedSteps = (current - baseline).toInt().coerceAtLeast(0)
                challengeProgressView?.text = PersianUtils.digits(completedSteps) + " از " + PersianUtils.digits(walkTarget) + " قدم"
                if (completedSteps >= walkTarget) {
                    stopChallengeSensor()
                    challengeDialog?.dismiss()
                    stopAndFinish()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        manager.registerListener(activeSensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun startShakeChallenge() {
        val target = when (shakeDifficulty) { "EASY" -> 12; "VERY_HARD" -> 45; else -> 28 }
        shakeCount = 0
        val manager = getSystemService(SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            AlertDialog.Builder(this).setMessage("حسگر حرکتی در دسترس نیست.").setPositiveButton("باشه", null).show()
            return
        }
        showProgressChallenge("گوشی را تکان بده", "۰ از " + PersianUtils.digits(target))
        sensorManager = manager
        activeSensor = sensor
        activeSensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val magnitude = kotlin.math.sqrt(event.values.sumOf { it.toDouble() * it.toDouble() }).toFloat()
                val now = System.currentTimeMillis()
                if (magnitude > 17f && now - lastShakeAt > 300L) {
                    lastShakeAt = now
                    shakeCount++
                    challengeProgressView?.text = PersianUtils.digits(shakeCount) + " از " + PersianUtils.digits(target)
                    if (shakeCount >= target) {
                        stopChallengeSensor()
                        challengeDialog?.dismiss()
                        stopAndFinish()
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        manager.registerListener(activeSensorListener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun startTypingChallenge() {
        val motivational = listOf(
            "من امروز با انرژی از خواب بیدار می‌شوم",
            "هر روز فرصتی تازه برای پیشرفت است",
            "قدم‌های کوچک من نتیجه‌های بزرگ می‌سازند",
            "من می‌توانم روزم را خوب شروع کنم"
        )
        val expected = when (typeMode) {
            "MOTIVATIONAL" -> motivational.random()
            "CUSTOM" -> customPhrase.ifBlank { "امروز روز خوبی است" }
            else -> buildString { repeat(8) { append("ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random()) } }
        }
        val input = EditText(this).apply {
            hint = "متن بالا را دقیق تایپ کن"
            setSingleLine(false)
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 10, 24, 4)
            addView(TextView(this@AlarmRingActivity).apply {
                text = expected
                textSize = 19f
                gravity = android.view.Gravity.CENTER
                setTextColor(getColor(R.color.accent_cyan))
                setPadding(0, 12, 0, 18)
            })
            addView(input)
        }
        val dialog = AlertDialog.Builder(this).setTitle("متن را تایپ کن").setView(content)
            .setCancelable(false).setNegativeButton("بررسی", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                if (input.text.toString().trim() == expected) {
                    dialog.dismiss(); stopAndFinish()
                } else input.error = "متن دقیقاً با نمونه یکسان نیست."
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_surface)
    }

    private fun startTouchChallenge() {
        var count = 0
        val target = touchTarget.coerceIn(1, 10000)
        val text = TextView(this).apply {
            text = "برای خاموش شدن، اینجا " + PersianUtils.digits(target) + " بار لمس کن"
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setTextColor(getColor(R.color.text_primary))
            setPadding(24, 48, 24, 48)
            background = androidx.core.content.ContextCompat.getDrawable(this@AlarmRingActivity, R.drawable.bg_surface_card)
        }
        val dialog = AlertDialog.Builder(this).setTitle("چالش لمس").setView(text).setCancelable(false).create()
        text.setOnClickListener {
            count++
            text.text = PersianUtils.digits(count) + " از " + PersianUtils.digits(target)
            if (count >= target) { dialog.dismiss(); stopAndFinish() }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_surface)
    }

    private fun startPatternChallenge() {
        val patternView = PatternChallengeView(this, patternSize)
        val dialog = AlertDialog.Builder(this).setTitle("الگو را بکش")
            .setMessage("یک الگوی پیوسته با حداقل ۳ نقطه رسم کن.")
            .setView(patternView).setCancelable(false)
            .setPositiveButton("بررسی الگو", null).setNegativeButton("پاک کردن", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                if (patternView.selectedCount >= 3) { dialog.dismiss(); stopAndFinish() }
                else android.widget.Toast.makeText(this, "الگو باید حداقل ۳ نقطه داشته باشد.", android.widget.Toast.LENGTH_SHORT).show()
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { patternView.clearPattern() }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_surface)
    }

    private class PatternChallengeView(context: android.content.Context, private val grid: Int) : View(context) {
        init {
            val minSide = (320 * resources.displayMetrics.density).toInt()
            minimumWidth = minSide
            minimumHeight = minSide
        }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val points = mutableListOf<Pair<Float, Float>>()
        private val selected = linkedSetOf<Int>()
        val selectedCount: Int get() = selected.size
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val side = minOf(width, height).toFloat() - 48f
            val left = (width - side) / 2f
            val top = (height - side) / 2f
            points.clear()
            for (r in 0 until grid) for (col in 0 until grid) {
                points.add(left + side * col / (grid - 1) to top + side * r / (grid - 1))
            }
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 8f; paint.color = context.getColor(R.color.accent_cyan)
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            points.forEachIndexed { index, point ->
                paint.color = if (index in selected) context.getColor(R.color.accent_violet) else context.getColor(R.color.text_secondary)
                canvas.drawCircle(point.first, point.second, 12f, paint)
            }
        }
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            if (event.action == android.view.MotionEvent.ACTION_DOWN || event.action == android.view.MotionEvent.ACTION_MOVE) {
                val side = minOf(width, height).toFloat() - 48f
                val left = (width - side) / 2f; val top = (height - side) / 2f
                var index = 0
                var best = Float.MAX_VALUE
                for (r in 0 until grid) for (col in 0 until grid) {
                    val x = left + side * col / (grid - 1); val y = top + side * r / (grid - 1)
                    val distance = kotlin.math.hypot(event.x - x, event.y - y)
                    if (distance < best) { best = distance; index = r * grid + col }
                }
                if (best < side / (grid - 1) * 0.45f && selected.add(index)) {
                    val pointX = left + side * (index % grid) / (grid - 1)
                    val pointY = top + side * (index / grid) / (grid - 1)
                    if (selected.size == 1) path.moveTo(pointX, pointY) else path.lineTo(pointX, pointY)
                    invalidate()
                }
                return true
            }
            return event.action == android.view.MotionEvent.ACTION_UP
        }
        fun clearPattern() { selected.clear(); path.reset(); invalidate() }
    }

    private fun startMemoryChallenge() {
        val answer = (1000..9999).random().toString()
        val dialog = AlertDialog.Builder(this).setTitle("عدد را به خاطر بسپار")
            .setMessage(answer).setCancelable(false).setPositiveButton("آماده‌ام", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                dialog.dismiss()
                val input = EditText(this).apply { hint = "عدد را وارد کن"; inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine(true) }
                AlertDialog.Builder(this).setTitle("عدد را وارد کن").setView(input).setCancelable(false)
                    .setPositiveButton("بررسی") { _, _ ->
                        if (input.text.toString() == answer) stopAndFinish()
                        else android.widget.Toast.makeText(this, "عدد درست نیست؛ دوباره تلاش کن.", android.widget.Toast.LENGTH_SHORT).show()
                    }.show()
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_surface)
        challengeHandler.postDelayed({ if (dialog.isShowing) dialog.setMessage("عدد پنهان شد؛ آماده‌ای؟") }, 2500L)
    }

    private fun startHoldChallenge() {
        binding.buttonDismiss.text = "برای ۸ ثانیه نگه دار"
        binding.buttonDismiss.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    holdStartedAt = System.currentTimeMillis()
                    holdRunnable = object : Runnable {
                        override fun run() {
                            val elapsed = System.currentTimeMillis() - holdStartedAt
                            binding.buttonDismiss.text = "نگه دار: " + PersianUtils.digits(((8000L - elapsed).coerceAtLeast(0L) / 1000L).toInt()) + " ثانیه"
                            if (elapsed >= 8000L) {
                                binding.buttonDismiss.setOnTouchListener(null)
                                stopAndFinish()
                            } else challengeHandler.postDelayed(this, 100L)
                        }
                    }
                    challengeHandler.post(holdRunnable!!)
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    challengeHandler.removeCallbacks(holdRunnable ?: return@setOnTouchListener true)
                    binding.buttonDismiss.text = "دوباره نگه دار"
                    true
                }
                else -> true
            }
        }
    }

    private fun stopAndFinish() {
        stopAlarmService()
        ringtone?.stop()
        vibrator?.cancel()
        NotificationHelper.cancelNotification(this, alarmId)
        if (deleteAfterRinging && alarmId != -1) {
            AlarmScheduler.cancel(this, Alarm(id = alarmId, hour = 0, minute = 0))
            lifecycleScope.launch {
                AlarmDatabase.getInstance(this@AlarmRingActivity).alarmDao().deleteById(alarmId)
                finish()
            }
        } else {
            finish()
        }
    }

    private fun snoozeAndFinish() {
        stopAlarmService()
        ringtone?.stop()
        vibrator?.cancel()
        NotificationHelper.cancelNotification(this, alarmId)
        SnoozeHelper.snooze(this, alarmId, minutes = 10,
            label = intent.getStringExtra("label") ?: "",
            ringtoneUri = intent.getStringExtra("ringtone_uri") ?: "",
            vibrate = intent.getBooleanExtra("vibrate", true),
            wakeMode = wakeMode,
            mathDifficulty = mathDifficulty,
            deleteAfterRinging = deleteAfterRinging,
            walkTarget = walkTarget,
            shakeDifficulty = shakeDifficulty,
            typeMode = typeMode,
            customPhrase = customPhrase,
            touchTarget = touchTarget,
            patternSize = patternSize)
        finish()
    }

    // Intentionally do not call super: the wake-up challenge must not be bypassed with Back.
    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Android API; kept to prevent bypassing wake-up challenges")
    override fun onBackPressed() {
        if (wakeMode == "NONE") stopAndFinish()
        else android.widget.Toast.makeText(this, "برای خاموش کردن هشدار، چالش را کامل کن.", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun stopAlarmService() {
        val stopIntent = Intent(this, AlarmRingingService::class.java).apply { action = AlarmRingingService.ACTION_STOP }
        startService(stopIntent)
    }

    override fun onDestroy() {
        stopChallengeSensor()
        challengeHandler.removeCallbacksAndMessages(null)
        iconPulse?.cancel()
        pulseAnimators.forEach { it.cancel() }
        tickHandler.removeCallbacks(tick)
        ringtone?.stop()
        vibrator?.cancel()
        super.onDestroy()
    }
}
