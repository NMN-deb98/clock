package com.example.alarmclock

import android.graphics.Bitmap
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
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.alarmclock.databinding.ActivityAlarmRingBinding
import kotlin.random.Random

class AlarmRingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAlarmRingBinding
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var alarmId: Int = -1
    private var wakeMode: String = "NONE"
    private var mathDifficulty: String = "MEDIUM"
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
        binding = ActivityAlarmRingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setShowWhenLockedAndTurnScreenOn()

        alarmId = intent.getIntExtra("alarm_id", -1)
        val label = intent.getStringExtra("label") ?: ""
        val ringtoneUriString = intent.getStringExtra("ringtone_uri") ?: ""
        val shouldVibrate = intent.getBooleanExtra("vibrate", true)
        wakeMode = intent.getStringExtra("wake_mode") ?: "NONE"
        mathDifficulty = intent.getStringExtra("math_difficulty") ?: "MEDIUM"
        binding.textNote.text = label.ifBlank { getString(R.string.no_note) }

        startRinging(ringtoneUriString)
        if (shouldVibrate) startVibrating()

        if (wakeMode == "MATH") {
            mathQuestions = createQuestions(mathDifficulty)
            binding.buttonDismiss.text = "حل سؤال‌ها"
        } else if (wakeMode == "PHOTO") {
            binding.buttonDismiss.text = "شروع چالش عکس"
        }
        binding.buttonDismiss.setOnClickListener {
            when (wakeMode) {
                "MATH" -> showMathQuestion()
                "PHOTO" -> capturePhoto()
                else -> stopAndFinish()
            }
        }
        binding.buttonSnooze.setOnClickListener { snoozeAndFinish() }
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

    private fun showMathQuestion() {
        if (mathIndex >= mathQuestions.size) {
            stopAndFinish()
            return
        }
        val question = mathQuestions[mathIndex]
        val input = EditText(this).apply {
            hint = "پاسخ عددی"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setSingleLine(true)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            addView(TextView(this@AlarmRingActivity).apply {
                text = "سؤال ${mathIndex + 1} از ۳:  ${question.prompt}"
                textSize = 20f
            })
            addView(input)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("برای خاموش کردن هشدار حل کن")
            .setView(content)
            .setCancelable(false)
            .setNegativeButton("بررسی", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                val answer = input.text.toString().trim().toIntOrNull()
                if (answer == question.answer) {
                    mathIndex++
                    dialog.dismiss()
                    if (mathIndex == 3) stopAndFinish() else showMathQuestion()
                } else {
                    input.error = "پاسخ درست نیست؛ دوباره تلاش کن"
                    input.text?.clear()
                }
            }
        }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text = "بررسی پاسخ"
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
            .setNegativeButton("بعداً", null)
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

    private fun stopAndFinish() {
        ringtone?.stop()
        vibrator?.cancel()
        NotificationHelper.cancelNotification(this, alarmId)
        finish()
    }

    private fun snoozeAndFinish() {
        ringtone?.stop()
        vibrator?.cancel()
        NotificationHelper.cancelNotification(this, alarmId)
        SnoozeHelper.snooze(this, alarmId, minutes = 10,
            label = intent.getStringExtra("label") ?: "",
            ringtoneUri = intent.getStringExtra("ringtone_uri") ?: "",
            vibrate = intent.getBooleanExtra("vibrate", true))
        finish()
    }

    override fun onDestroy() {
        ringtone?.stop()
        vibrator?.cancel()
        super.onDestroy()
    }
}
