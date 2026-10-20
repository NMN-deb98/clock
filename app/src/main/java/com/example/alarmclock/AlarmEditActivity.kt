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

class AlarmEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmEditBinding
    private lateinit var viewModel: AlarmViewModel

    private var alarmId: Int = -1
    private var existingAlarm: Alarm? = null

    private var selectedHour: Int = 8
    private var selectedMinute: Int = 0
    private var selectedRingtoneUri: String = ""
    private var selectedRingtoneName: String = "پیش‌فرض سیستم"

    // روزهای انتخاب‌شده - کلید بر اساس java.util.Calendar
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
                    val ringtone = RingtoneManager.getRingtone(this, uri)
                    selectedRingtoneName = ringtone?.getTitle(this) ?: "صدای انتخاب‌شده"
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

        viewModel = ViewModelProvider(this)[AlarmViewModel::class.java]
        alarmId = intent.getIntExtra("alarm_id", -1)

        setupDayToggles()

        binding.timePicker.setIs24HourView(true)
        binding.spinnerMathDifficulty.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("آسان", "متوسط", "سخت")
        )
        binding.wakeModeGroup.setOnCheckedChangeListener { _, checkedId ->
            binding.mathDifficultyContainer.visibility =
                if (checkedId == binding.wakeModeMath.id) View.VISIBLE else View.GONE
        }

        if (alarmId != -1) {
            title = getString(R.string.edit_alarm)
            binding.buttonDelete.visibility = android.view.View.VISIBLE
            lifecycleScope.launch {
                val alarm = viewModel.getById(alarmId)
                if (alarm != null) {
                    existingAlarm = alarm
                    fillFields(alarm)
                }
            }
        } else {
            title = getString(R.string.new_alarm)
            binding.buttonDelete.visibility = android.view.View.GONE
            binding.timePicker.hour = selectedHour
            binding.timePicker.minute = selectedMinute
            binding.textRingtone.text = selectedRingtoneName
        }

        binding.rowRingtone.setOnClickListener { openRingtonePicker() }

        binding.buttonSave.setOnClickListener { saveAlarm() }

        binding.buttonDelete.setOnClickListener {
            existingAlarm?.let {
                AlarmScheduler.cancel(this, it)
                viewModel.delete(it)
            }
            finish()
        }
    }

    private fun setupDayToggles() {
        val dayChips = listOf(
            binding.chipSat to Calendar.SATURDAY,
            binding.chipSun to Calendar.SUNDAY,
            binding.chipMon to Calendar.MONDAY,
            binding.chipTue to Calendar.TUESDAY,
            binding.chipWed to Calendar.WEDNESDAY,
            binding.chipThu to Calendar.THURSDAY,
            binding.chipFri to Calendar.FRIDAY
        )
        dayChips.forEach { (chip, day) ->
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedDays.add(day) else selectedDays.remove(day)
            }
        }
    }

    private fun fillFields(alarm: Alarm) {
        binding.timePicker.hour = alarm.hour
        binding.timePicker.minute = alarm.minute
        binding.editNote.setText(alarm.label)
        binding.checkVibrate.isChecked = alarm.vibrate
        when (alarm.wakeMode) {
            "MATH" -> binding.wakeModeMath.isChecked = true
            "PHOTO" -> binding.wakeModePhoto.isChecked = true
            else -> binding.wakeModeNone.isChecked = true
        }
        binding.spinnerMathDifficulty.setSelection(
            when (alarm.mathDifficulty) { "EASY" -> 0; "HARD" -> 2; else -> 1 }
        )

        selectedRingtoneUri = alarm.ringtoneUri
        selectedRingtoneName = alarm.ringtoneName
        binding.textRingtone.text = selectedRingtoneName

        selectedDays.clear()
        selectedDays.addAll(alarm.dayList())

        val map = mapOf(
            Calendar.SATURDAY to binding.chipSat, Calendar.SUNDAY to binding.chipSun,
            Calendar.MONDAY to binding.chipMon, Calendar.TUESDAY to binding.chipTue,
            Calendar.WEDNESDAY to binding.chipWed, Calendar.THURSDAY to binding.chipThu,
            Calendar.FRIDAY to binding.chipFri
        )
        map.forEach { (day, chip) -> chip.isChecked = selectedDays.contains(day) }
    }

    private fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            )
            if (selectedRingtoneUri.isNotBlank()) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(selectedRingtoneUri))
            }
        }
        ringtonePickerLauncher.launch(intent)
    }

    private fun saveAlarm() {
        val hour = binding.timePicker.hour
        val minute = binding.timePicker.minute
        val note = binding.editNote.text?.toString()?.trim() ?: ""
        val vibrate = binding.checkVibrate.isChecked
        val daysString = selectedDays.sorted().joinToString(",")
        val wakeMode = when (binding.wakeModeGroup.checkedRadioButtonId) {
            binding.wakeModeMath.id -> "MATH"
            binding.wakeModePhoto.id -> "PHOTO"
            else -> "NONE"
        }
        val mathDifficulty = when (binding.spinnerMathDifficulty.selectedItemPosition) {
            0 -> "EASY"
            2 -> "HARD"
            else -> "MEDIUM"
        }

        val alarm = (existingAlarm ?: Alarm(hour = hour, minute = minute)).copy(
            hour = hour,
            minute = minute,
            days = daysString,
            label = note,
            ringtoneUri = selectedRingtoneUri,
            ringtoneName = selectedRingtoneName,
            vibrate = vibrate,
            enabled = true,
            wakeMode = wakeMode,
            mathDifficulty = mathDifficulty
        )

        if (existingAlarm != null) {
            AlarmScheduler.cancel(this, existingAlarm!!)
            viewModel.update(alarm)
            AlarmScheduler.schedule(this, alarm)
            finish()
        } else {
            viewModel.insert(alarm) { inserted ->
                AlarmScheduler.schedule(this, inserted)
                finish()
            }
        }
    }
}
