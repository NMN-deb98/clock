package com.example.alarmclock

import android.Manifest
import android.animation.ObjectAnimator
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.TextView
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
import com.google.android.material.snackbar.Snackbar
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: AlarmViewModel
    private lateinit var adapter: AlarmAdapter
    private var latestAlarms: List<Alarm> = emptyList()
    private var fullScreenSettingsLaunched = false
    private var layoutAnimationPlayed = false
    private var heroPulse: ObjectAnimator? = null
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
        updateLiveIconLabel()
        binding.buttonLiveIcon.setOnClickListener {
            AppIconSwitcher.setLive(this, !AppIconSwitcher.isLive(this))
            updateLiveIconLabel()
        }
        NotificationHelper.createChannel(this)
        askNotificationPermissionIfNeeded()
        askExactAlarmPermissionIfNeeded()
        viewModel = ViewModelProvider(this)[AlarmViewModel::class.java]
        adapter = AlarmAdapter(
            onItemClick = { alarm ->
                open(AlarmEditActivity::class.java) { it.putExtra("alarm_id", alarm.id) }
            },
            onToggle = { alarm, enabled ->
                val updated = alarm.copy(enabled = enabled)
                viewModel.update(updated)
                if (enabled) AlarmScheduler.schedule(this, updated) else AlarmScheduler.cancel(this, updated)
            },
            onDelete = { alarm ->
                AlarmScheduler.cancel(this, alarm)
                viewModel.delete(alarm)
                showUndo(alarm)
            }
        )
        binding.recyclerAlarms.layoutManager = LinearLayoutManager(this)
        binding.recyclerAlarms.adapter = adapter
        viewModel.allAlarms.observe(this) { alarms ->
            latestAlarms = alarms.orEmpty()
            adapter.submitList(latestAlarms) {
                if (!layoutAnimationPlayed && latestAlarms.isNotEmpty()) {
                    layoutAnimationPlayed = true
                    binding.recyclerAlarms.scheduleLayoutAnimation()
                }
            }
            binding.emptyView.visibility =
                if (latestAlarms.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.textAlarmCount.text =
                if (latestAlarms.isEmpty()) "" else PersianUtils.digits(latestAlarms.size) + " هشدار"
            updateNextAlarm()
        }
        binding.fabAdd.setOnClickListener { open(AlarmEditActivity::class.java) }
        binding.buttonHome.setOnClickListener { binding.recyclerAlarms.smoothScrollToPosition(0) }
        binding.buttonWorldClock.setOnClickListener { open(WorldClockActivity::class.java) }
        binding.buttonStopwatch.setOnClickListener { open(StopwatchActivity::class.java) }
        binding.buttonTimer.setOnClickListener { open(CountdownTimerActivity::class.java) }
        binding.buttonDeskClock.setOnClickListener { open(DeskClockActivity::class.java) }

        listOf(
            binding.buttonHome, binding.buttonWorldClock, binding.buttonStopwatch,
            binding.buttonTimer, binding.buttonDeskClock
        ).forEach { UiFx.pressable(it, 0.9f) }
        UiFx.pressable(binding.fabAdd, 0.94f)

        refreshHeader()
        if (savedInstanceState == null) {
            UiFx.enter(binding.headerBlock, 0L, 14f)
            UiFx.enter(binding.heroCard, 110L)
        }
    }

    private fun open(target: Class<*>, extra: ((Intent) -> Unit)? = null) {
        val intent = Intent(this, target)
        extra?.invoke(intent)
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.screen_in, R.anim.screen_out)
    }

    private fun showUndo(alarm: Alarm) {
        Snackbar.make(binding.root, "هشدار حذف شد", Snackbar.LENGTH_LONG)
            .setAnchorView(binding.bottomNavigation)
            .setBackgroundTint(getColor(R.color.surface_elevated))
            .setTextColor(getColor(R.color.text_primary))
            .setActionTextColor(getColor(R.color.accent_cyan))
            .setAction("بازگردانی") {
                viewModel.insert(alarm) { inserted ->
                    if (inserted.enabled) AlarmScheduler.schedule(this, inserted)
                }
            }
            .show()
    }

    private fun refreshHeader() {
        val cal = PersianUtils.newCalendar(TimeZone.getDefault(), System.currentTimeMillis())
        binding.textGreeting.text = PersianUtils.greeting(cal.get(Calendar.HOUR_OF_DAY))
        binding.textTodayDate.text = PersianUtils.dateText(cal)
    }

    private fun updateNextAlarm() {
        val current = Calendar.getInstance()
        val currentHour = current.get(Calendar.HOUR_OF_DAY)
        binding.heroIcon.setImageResource(if (currentHour in 6..17) R.drawable.ic_sun else R.drawable.ic_moon)
        binding.heroIcon.rotation = current.get(Calendar.MINUTE) * 6f + current.get(Calendar.SECOND) * 0.1f
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
        if (days > 0) parts.add("${PersianUtils.digits(days)} روز")
        if (hours > 0 || days > 0) parts.add("${PersianUtils.digits(hours)} ساعت")
        if (minutes > 0 || hours > 0 || days > 0) parts.add("${PersianUtils.digits(minutes)} دقیقه")
        parts.add("${PersianUtils.digits(seconds)} ثانیه")
        binding.textNextAlarmCountdown.text = "تا زنگ بعدی: " + parts.joinToString(" و ")

        val clock = PersianUtils.two(next.first.hour) + ":" + PersianUtils.two(next.first.minute)
        binding.textNextAlarmTime.text = "${next.first.label.ifBlank { "هشدار بعدی" }} • ساعت $clock"
    }

    private fun updateLiveIconLabel() {
        binding.buttonLiveIcon.text = if (AppIconSwitcher.isLive(this)) "آیکون برنامه با ساعت عوض می‌شود  ●" else "آیکون برنامه ثابت است  ○"
    }

    override fun onResume() {
        super.onResume()
        AppIconSwitcher.apply(this)
        AppIconSwitcher.scheduleNext(this)
        DeskClockWidgetProvider.refreshAll(this)
        refreshHeader()
        countdownHandler.removeCallbacks(countdownTick)
        countdownHandler.post(countdownTick)
        heroPulse?.cancel()
        heroPulse = UiFx.breathe(binding.heroIconFrame, 1.08f, 1800L)
        requestFullScreenAlarmAccessIfNeeded()
    }

    private var readinessBanner: android.widget.LinearLayout? = null

    /** Shows what still blocks the alarm screen from opening over the lock screen, with one tap to fix it. */
    private fun requestFullScreenAlarmAccessIfNeeded() {
        val column = binding.contentColumn
        readinessBanner?.let { column.removeView(it) }
        readinessBanner = null
        val prefs = getSharedPreferences("alarm_preferences", MODE_PRIVATE)
        if (System.currentTimeMillis() < prefs.getLong("readiness_later_until", 0L)) return
        val issue = LockScreenReadiness.issues(this).firstOrNull() ?: return
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val banner = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = UiFx.rounded(this@MainActivity, getColor(R.color.surface_elevated), 20, getColor(R.color.accent_amber))
            setPadding(dp(16), dp(14), dp(16), dp(10))
            addView(TextView(this@MainActivity).apply {
                text = "⚠  " + issue.title
                textSize = 15f
                setTextColor(getColor(R.color.accent_amber))
                typeface = UiFx.font(this@MainActivity, 700)
            })
            addView(TextView(this@MainActivity).apply {
                text = issue.body
                textSize = 13f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, dp(6), 0, dp(10))
            })
            val row = android.widget.LinearLayout(this@MainActivity).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
            row.addView(TextView(this@MainActivity).apply {
                text = "باز کردن تنظیمات"
                textSize = 14f
                setTextColor(getColor(R.color.app_background))
                background = UiFx.rounded(this@MainActivity, getColor(R.color.accent_cyan), 14)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                setOnClickListener { runCatching { startActivity(LockScreenReadiness.settingsIntent(this@MainActivity, issue)) } }
            })
            row.addView(TextView(this@MainActivity).apply {
                text = "بعداً"
                textSize = 14f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(dp(18), dp(10), dp(18), dp(10))
                setOnClickListener {
                    prefs.edit().putLong("readiness_later_until", System.currentTimeMillis() + 3L * 24 * 3600 * 1000).apply()
                    requestFullScreenAlarmAccessIfNeeded()
                }
            })
            addView(row)
        }
        column.addView(banner, 0, android.widget.LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(8), 0, dp(8))
        })
        readinessBanner = banner
    }

    override fun onPause() {
        countdownHandler.removeCallbacks(countdownTick)
        heroPulse?.cancel()
        binding.heroIconFrame.scaleX = 1f
        binding.heroIconFrame.scaleY = 1f
        super.onPause()
    }

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
