package com.example.alarmclock

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.alarmclock.databinding.ActivityMainBinding
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: AlarmViewModel
    private lateinit var adapter: AlarmAdapter
    private var latestAlarms: List<Alarm> = emptyList()
    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownTick = object : Runnable {
        override fun run() {
            updateNextAlarm()
            countdownHandler.postDelayed(this, 1_000)
        }
    }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.decorView.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        NotificationHelper.createChannel(this)
        askNotificationPermissionIfNeeded()
        askExactAlarmPermissionIfNeeded()
        viewModel = ViewModelProvider(this)[AlarmViewModel::class.java]
        adapter = AlarmAdapter(
            onItemClick = { alarm ->
                startActivity(Intent(this, AlarmEditActivity::class.java).putExtra("alarm_id", alarm.id))
            },
            onToggle = { alarm, enabled ->
                val updated = alarm.copy(enabled = enabled)
                viewModel.update(updated)
                if (enabled) AlarmScheduler.schedule(this, updated) else AlarmScheduler.cancel(this, updated)
            },
            onDelete = { alarm -> AlarmScheduler.cancel(this, alarm); viewModel.delete(alarm) }
        )
        binding.recyclerAlarms.layoutManager = LinearLayoutManager(this)
        binding.recyclerAlarms.adapter = adapter
        viewModel.allAlarms.observe(this) { alarms ->
            latestAlarms = alarms.orEmpty()
            adapter.submitList(latestAlarms)
            binding.emptyView.visibility = if (latestAlarms.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            updateNextAlarm()
        }
        binding.fabAdd.setOnClickListener { startActivity(Intent(this, AlarmEditActivity::class.java)) }
        binding.buttonHome.setOnClickListener { binding.recyclerAlarms.smoothScrollToPosition(0) }
        binding.buttonWorldClock.setOnClickListener { startActivity(Intent(this, WorldClockActivity::class.java)) }
        binding.buttonStopwatch.setOnClickListener { startActivity(Intent(this, StopwatchActivity::class.java)) }
        binding.buttonTimer.setOnClickListener { startActivity(Intent(this, CountdownTimerActivity::class.java)) }
    }

    private fun updateNextAlarm() {
        val next = latestAlarms.filter { it.enabled }
            .map { it to AlarmScheduler.nextTriggerTimeMillis(it) }
            .minByOrNull { it.second }
        if (next == null) {
            binding.textNextAlarmCountdown.text = "هنوز هشدار فعالی نداری"
            binding.textNextAlarmTime.text = "برای شروع، یک هشدار جدید اضافه کن."
            return
        }

        val remaining = (next.second - System.currentTimeMillis()).coerceAtLeast(0L)
        val days = TimeUnit.MILLISECONDS.toDays(remaining)
        val hours = TimeUnit.MILLISECONDS.toHours(remaining) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
        val parts = mutableListOf<String>()
        if (days > 0) parts.add("${persianNumber(days)} روز")
        if (hours > 0 || days > 0) parts.add("${persianNumber(hours)} ساعت")
        if (minutes > 0 || hours > 0 || days > 0) parts.add("${persianNumber(minutes)} دقیقه")
        parts.add("${persianNumber(seconds)} ثانیه")
        binding.textNextAlarmCountdown.text = "تا زنگ بعدی: " + parts.joinToString(" و ")

        val clock = String.format(Locale("fa", "IR"), "%02d:%02d", next.first.hour, next.first.minute)
        binding.textNextAlarmTime.text = "${next.first.label.ifBlank { "هشدار بعدی" }} • ساعت $clock"
    }

    private fun persianNumber(number: Long): String =
        number.toString().map { ch ->
            if (ch in '0'..'9') ('۰'.code + (ch - '0')).toChar() else ch
        }.joinToString("")

    override fun onResume() { super.onResume(); countdownHandler.removeCallbacks(countdownTick); countdownHandler.post(countdownTick) }
    override fun onPause() { countdownHandler.removeCallbacks(countdownTick); super.onPause() }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    private fun askExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!manager.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }
    }
}