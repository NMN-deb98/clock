package com.example.alarmclock

import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.alarmclock.databinding.ActivityAlarmRingBinding

class AlarmRingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmRingBinding
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var alarmId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmRingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setShowWhenLockedAndTurnScreenOn()

        alarmId = intent.getIntExtra("alarm_id", -1)
        val label = intent.getStringExtra("label") ?: ""
        val ringtoneUriString = intent.getStringExtra("ringtone_uri") ?: ""
        val shouldVibrate = intent.getBooleanExtra("vibrate", true)

        binding.textNote.text = label.ifBlank { getString(R.string.no_note) }

        startRinging(ringtoneUriString)
        if (shouldVibrate) startVibrating()

        binding.buttonDismiss.setOnClickListener { stopAndFinish() }
        binding.buttonSnooze.setOnClickListener { snoozeAndFinish() }
    }

    private fun setShowWhenLockedAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
    }

    private fun startRinging(uriString: String) {
        val uri: Uri = if (uriString.isNotBlank())
            Uri.parse(uriString)
        else
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = true
            play()
        }
    }

    private fun startVibrating() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 800, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
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
        SnoozeHelper.snooze(this, alarmId, minutes = 10)
        finish()
    }

    override fun onDestroy() {
        ringtone?.stop()
        vibrator?.cancel()
        super.onDestroy()
    }
}
