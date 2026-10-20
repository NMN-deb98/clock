package com.example.alarmclock

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.alarmclock.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: AlarmViewModel
    private lateinit var adapter: AlarmAdapter

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper.createChannel(this)
        askNotificationPermissionIfNeeded()
        askExactAlarmPermissionIfNeeded()

        viewModel = ViewModelProvider(this)[AlarmViewModel::class.java]

        adapter = AlarmAdapter(
            onItemClick = { alarm ->
                val intent = Intent(this, AlarmEditActivity::class.java)
                intent.putExtra("alarm_id", alarm.id)
                startActivity(intent)
            },
            onToggle = { alarm, isEnabled ->
                val updated = alarm.copy(enabled = isEnabled)
                viewModel.update(updated)
                if (isEnabled) {
                    AlarmScheduler.schedule(this, updated)
                } else {
                    AlarmScheduler.cancel(this, updated)
                }
            },
            onDelete = { alarm ->
                AlarmScheduler.cancel(this, alarm)
                viewModel.delete(alarm)
            }
        )

        binding.recyclerAlarms.layoutManager = LinearLayoutManager(this)
        binding.recyclerAlarms.adapter = adapter

        viewModel.allAlarms.observe(this) { alarms ->
            adapter.submitList(alarms)
            binding.emptyView.visibility =
                if (alarms.isNullOrEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AlarmEditActivity::class.java))
        }
        binding.buttonWorldClock.setOnClickListener {
            startActivity(Intent(this, WorldClockActivity::class.java))
        }
        binding.buttonStopwatch.setOnClickListener {
            startActivity(Intent(this, StopwatchActivity::class.java))
        }
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun askExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
}
