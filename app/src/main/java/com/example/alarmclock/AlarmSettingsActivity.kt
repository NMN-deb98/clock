package com.example.alarmclock

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView

class AlarmSettingsActivity : BaseActivity() {
    private val prefs by lazy { getSharedPreferences("alarm_preferences", MODE_PRIVATE) }
    private lateinit var volumeLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(getColor(R.color.app_background))
        }
        fun title(text: String, size: Float = 18f) = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(getColor(R.color.text_primary))
            setPadding(0, dp(12), 0, dp(8))
        }
        fun label(text: String) = TextView(this).apply {
            this.text = text; textSize = 14f; setTextColor(getColor(R.color.text_secondary))
        }
        root.addView(title("تنظیمات هشدار", 26f))
        root.addView(label("صدای زنگ هشدار را مطابق سلیقه‌ات تنظیم کن."))
        volumeLabel = title("بلندی صدا: " + prefs.getInt("volume", 75) + "٪", 16f)
        root.addView(volumeLabel)
        root.addView(SeekBar(this).apply {
            max = 100; progress = prefs.getInt("volume", 75)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    volumeLabel.text = "بلندی صدا: " + progress + "٪"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            tag = "volume"
        })
        val gradual = CheckBox(this).apply {
            text = "افزایش تدریجی بلندی صدا"
            setTextColor(getColor(R.color.text_primary))
            isChecked = prefs.getBoolean("gradual_volume", true)
        }
        root.addView(gradual)
        val loud = CheckBox(this).apply {
            text = "اگر پاسخ ندادم، بعد از ۳۰ ثانیه صدای خیلی بلند پخش شود"
            setTextColor(getColor(R.color.text_primary))
            isChecked = prefs.getBoolean("loud_escalation", false)
        }
        root.addView(loud)
        val holidays = CheckBox(this).apply {
            text = "خاموش شدن هشدار در تعطیلات رسمی ثابت ایران"
            setTextColor(getColor(R.color.text_primary))
            isChecked = prefs.getBoolean("disable_holidays", false)
        }
        root.addView(holidays)
        root.addView(label("این گزینه تعطیلات ثابت شمسی را پوشش می‌دهد؛ تعطیلات قمریِ متغیر در این نسخه لحاظ نشده‌اند."))
        root.addView(label("تنظیمات چرت زدن حالا برای هر هشدار جداگانه، داخل صفحهٔ ساخت/ویرایش هشدار قرار دارد."))
        root.addView(label("تنظیم بلندی صدا روی جریان صدای هشدار دستگاه اعمال می‌شود."))
        root.addView(Button(this).apply {
            text = "ذخیره تنظیمات"
            setOnClickListener {
                val volume = root.findViewWithTag<SeekBar>("volume").progress
                prefs.edit()
                    .putInt("volume", volume)
                    .putBoolean("gradual_volume", gradual.isChecked)
                    .putBoolean("loud_escalation", loud.isChecked)
                    .putBoolean("disable_holidays", holidays.isChecked)
                    .apply()
                finish()
            }
        }.apply { setPadding(0, dp(8), 0, dp(8)) })
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }
}