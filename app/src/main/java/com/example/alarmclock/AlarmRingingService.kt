package com.example.alarmclock

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class AlarmRingingService : Service() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: android.media.AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlayback()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        val id = intent.getIntExtra("alarm_id", -1)
        if (id < 0) { stopSelf(); return START_NOT_STICKY }
        val label = intent.getStringExtra("label").orEmpty()
        val uri = intent.getStringExtra("ringtone_uri").orEmpty()
        val vibrate = intent.getBooleanExtra("vibrate", true)
        val notification = NotificationHelper.buildAlarmNotification(
            this, id, label, uri, vibrate,
            intent.getStringExtra("wake_mode") ?: "NONE",
            intent.getStringExtra("math_difficulty") ?: "MEDIUM",
            intent.getBooleanExtra("delete_after_ringing", false)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(id, notification)
        startPlayback(uri, vibrate)
        return START_NOT_STICKY
    }

    private fun startPlayback(uriString: String, shouldVibrate: Boolean) {
        stopPlayback()
        if (uriString != "silent") {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioManager?.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
            val defaultUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val requestedUri = if (uriString.isNotBlank()) runCatching { Uri.parse(uriString) }.getOrNull() else defaultUri
            val requestedTone = requestedUri?.let { runCatching { RingtoneManager.getRingtone(this, it) }.getOrNull() }
            val selectedTone = requestedTone ?: defaultUri?.let { runCatching { RingtoneManager.getRingtone(this, it) }.getOrNull() }
            ringtone = selectedTone?.apply {
                audioAttributes = attributes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                runCatching { play() }
            }
        }
        if (shouldVibrate) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 800, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun stopPlayback() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
        audioManager = null
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.alarmclock.action.STOP_ALARM_SOUND"
    }
}
