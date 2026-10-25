package com.example.alarmclock

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.alarmclock.databinding.ActivityAlarmEditBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmEditActivity : BaseActivity() {
    private lateinit var binding: ActivityAlarmEditBinding
    private lateinit var viewModel: AlarmViewModel
    private var alarmId: Int = -1
    private var existingAlarm: Alarm? = null
    private var selectedHour: Int = 7
    private var selectedMinute: Int = 0
    private var selectedRingtoneUri: String = ""
    private var selectedRingtoneName: String = "پیش‌فرض سیستم"
    private val selectedDays = linkedSetOf<Int>()

    private val ringtonePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                }
                if (uri != null) {
                    selectedRingtoneUri = uri.toString()
                    selectedRingtoneName = RingtoneManager.getRingtone(this, uri)?.getTitle(this) ?: "صدای انتخاب‌شده"
                } else {
                    selectedRingtoneUri = "silent"
                    selectedRingtoneName = "بدون صدا"
                }
                binding.textRingtone.text = selectedRingtoneName
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        viewModel = ViewModelProvider(this)[AlarmViewModel::class.java]
        alarmId = intent.getIntExtra("alarm_id", -1)
        setupDayToggles()
        binding.timePicker.setIs24HourView(true)
        binding.spinnerMathDifficulty.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, listOf("آسان", "متوسط", "سخت")
        )
        binding.spinnerShakeDifficulty.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, listOf("آسان", "سخت", "خیلی سخت")
        )
        binding.spinnerTypeMode.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, listOf("حروف تصادفی", "جملات انگیزشی", "جمله دلخواه")
        )
        binding.spinnerPatternSize.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, listOf("۳ × ۳", "۴ × ۴", "۵ × ۵")
        )
        binding.wakeModeGroup.setOnCheckedChangeListener { _, checkedId ->
            binding.mathDifficultyContainer.visibility =
                if (checkedId == binding.wakeModeMath.id) View.VISIBLE else View.GONE
            binding.otherChallengeContainer.visibility =
                if (checkedId in listOf(binding.wakeModeWalk.id, binding.wakeModeShake.id,
                    binding.wakeModeType.id, binding.wakeModeTouch.id, binding.wakeModePattern.id)) View.VISIBLE else View.GONE
            binding.editWalkTarget.visibility = if (checkedId == binding.wakeModeWalk.id) View.VISIBLE else View.GONE
            binding.editTouchTarget.visibility = if (checkedId == binding.wakeModeTouch.id) View.VISIBLE else View.GONE
            binding.spinnerShakeDifficulty.visibility = if (checkedId == binding.wakeModeShake.id) View.VISIBLE else View.GONE
            binding.spinnerTypeMode.visibility = if (checkedId == binding.wakeModeType.id) View.VISIBLE else View.GONE
            binding.editCustomPhrase.visibility = if (checkedId == binding.wakeModeType.id && binding.spinnerTypeMode.selectedItemPosition == 2) View.VISIBLE else View.GONE
            binding.spinnerPatternSize.visibility = if (checkedId == binding.wakeModePattern.id) View.VISIBLE else View.GONE
        }
        binding.spinnerTypeMode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.editCustomPhrase.visibility = if (binding.wakeModeType.isChecked && position == 2) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        if (alarmId != -1) {
            title = getString(R.string.edit_alarm)
            binding.buttonDelete.visibility = View.VISIBLE
            lifecycleScope.launch {
                viewModel.getById(alarmId)?.let { existingAlarm = it; fillFields(it) }
            }
        } else {
            title = getString(R.string.new_alarm)
            binding.buttonDelete.visibility = View.GONE
            binding.timePicker.hour = selectedHour
            binding.timePicker.minute = selectedMinute
            binding.textRingtone.text = selectedRingtoneName
            binding.checkDeleteAfterRinging.isChecked = false
        }

        binding.rowRingtone.setOnClickListener { openRingtonePicker() }
        binding.buttonSave.setOnClickListener { saveAlarm() }
        binding.buttonDelete.setOnClickListener {
            existingAlarm?.let { AlarmScheduler.cancel(this, it); viewModel.delete(it) }
            finish()
        }
    }

    private fun setupDayToggles() {
        val dayChips = listOf(
            binding.chipSat to Calendar.SATURDAY, binding.chipSun to Calendar.SUNDAY,
            binding.chipMon to Calendar.MONDAY, binding.chipTue to Calendar.TUESDAY,
            binding.chipWed to Calendar.WEDNESDAY, binding.chipThu to Calendar.THURSDAY,
            binding.chipFri to Calendar.FRIDAY
        )
        dayChips.forEach { (chip, day) ->
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedDays.add(day) else selectedDays.remove(day)
            }
        }
    }

    private fun fillFields(alarm: Alarm) {
        binding.timePicker.hour = alarm.hour
        binding.timePicker.minute = alarm.minute
        binding.editNote.setText(alarm.label)
        binding.checkVibrate.isChecked = alarm.vibrate
        binding.checkDeleteAfterRinging.isChecked = alarm.deleteAfterRinging
        when (alarm.wakeMode) {
            "MATH" -> binding.wakeModeMath.isChecked = true
            "PHOTO" -> binding.wakeModePhoto.isChecked = true
            "WALK" -> binding.wakeModeWalk.isChecked = true
            "SHAKE" -> binding.wakeModeShake.isChecked = true
            "TYPE" -> binding.wakeModeType.isChecked = true
            "TOUCH" -> binding.wakeModeTouch.isChecked = true
            "PATTERN" -> binding.wakeModePattern.isChecked = true
            "MEMORY" -> binding.wakeModeMemory.isChecked = true
            "HOLD" -> binding.wakeModeHold.isChecked = true
            else -> binding.wakeModeNone.isChecked = true
        }
        binding.editWalkTarget.setText(alarm.walkTarget.toString())
        binding.editTouchTarget.setText(alarm.touchTarget.toString())
        binding.spinnerShakeDifficulty.setSelection(when (alarm.shakeDifficulty) { "EASY" -> 0; "VERY_HARD" -> 2; else -> 1 })
        binding.spinnerTypeMode.setSelection(when (alarm.typeMode) { "MOTIVATIONAL" -> 1; "CUSTOM" -> 2; else -> 0 })
        binding.editCustomPhrase.setText(alarm.customPhrase)
        binding.spinnerPatternSize.setSelection((alarm.patternSize - 3).coerceIn(0, 2))
        binding.spinnerMathDifficulty.setSelection(when (alarm.mathDifficulty) {
            "EASY" -> 0; "HARD" -> 2; else -> 1
        })
        selectedRingtoneUri = alarm.ringtoneUri
        selectedRingtoneName = alarm.ringtoneName
        binding.textRingtone.text = selectedRingtoneName
        selectedDays.clear()
        selectedDays.addAll(alarm.dayList())
        mapOf(
            Calendar.SATURDAY to binding.chipSat, Calendar.SUNDAY to binding.chipSun,
            Calendar.MONDAY to binding.chipMon, Calendar.TUESDAY to binding.chipTue,
            Calendar.WEDNESDAY to binding.chipWed, Calendar.THURSDAY to binding.chipThu,
            Calendar.FRIDAY to binding.chipFri
        ).forEach { (day, chip) -> chip.isChecked = selectedDays.contains(day) }
    }

    private fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            if (selectedRingtoneUri.isNotBlank()) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(selectedRingtoneUri))
        }
        ringtonePickerLauncher.launch(intent)
    }

    private fun saveAlarm() {
        val hour = binding.timePicker.hour
        val minute = binding.timePicker.minute
        val note = binding.editNote.text?.toString()?.trim() ?: ""
        val daysString = selectedDays.sorted().joinToString(",")
        val wakeMode = when (binding.wakeModeGroup.checkedRadioButtonId) {
            binding.wakeModeMath.id -> "MATH"
            binding.wakeModePhoto.id -> "PHOTO"
            binding.wakeModeWalk.id -> "WALK"
            binding.wakeModeShake.id -> "SHAKE"
            binding.wakeModeType.id -> "TYPE"
            binding.wakeModeTouch.id -> "TOUCH"
            binding.wakeModePattern.id -> "PATTERN"
            binding.wakeModeMemory.id -> "MEMORY"
            binding.wakeModeHold.id -> "HOLD"
            else -> "NONE"
        }
        val mathDifficulty = when (binding.spinnerMathDifficulty.selectedItemPosition) {
            0 -> "EASY"; 2 -> "HARD"; else -> "MEDIUM"
        }
        val alarm = (existingAlarm ?: Alarm(hour = hour, minute = minute)).copy(
            hour = hour, minute = minute, days = daysString, label = note,
            ringtoneUri = selectedRingtoneUri, ringtoneName = selectedRingtoneName,
            vibrate = binding.checkVibrate.isChecked, enabled = true,
            wakeMode = wakeMode, mathDifficulty = mathDifficulty,
            deleteAfterRinging = binding.checkDeleteAfterRinging.isChecked,
            walkTarget = binding.editWalkTarget.text.toString().toIntOrNull()?.coerceIn(1, 10000) ?: 40,
            shakeDifficulty = when (binding.spinnerShakeDifficulty.selectedItemPosition) { 0 -> "EASY"; 2 -> "VERY_HARD"; else -> "HARD" },
            typeMode = when (binding.spinnerTypeMode.selectedItemPosition) { 1 -> "MOTIVATIONAL"; 2 -> "CUSTOM"; else -> "RANDOM" },
            customPhrase = binding.editCustomPhrase.text.toString().trim(),
            touchTarget = binding.editTouchTarget.text.toString().toIntOrNull()?.coerceIn(1, 10000) ?: 30,
            patternSize = binding.spinnerPatternSize.selectedItemPosition + 3
        )
        if (existingAlarm != null) {
            AlarmScheduler.cancel(this, existingAlarm!!)
            viewModel.update(alarm)
            AlarmScheduler.schedule(this, alarm)
            finish()
        } else {
            viewModel.insert(alarm) { inserted -> AlarmScheduler.schedule(this, inserted); finish() }
        }
    }
}