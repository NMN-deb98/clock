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
    private val volumeHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var originalAlarmVolume: Int? = null
    private var targetAlarmVolume: Int = 0
    private var volumeStartedAt: Long = 0L
    private var gradualVolume: Boolean = false
    private var loudEscalation: Boolean = false
    private val volumeTick = object : Runnable {
        override fun run() {
            val manager = audioManager ?: return
            val max = manager.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
            val elapsed = System.currentTimeMillis() - volumeStartedAt
            val next = when {
                loudEscalation && elapsed >= 30_000L -> max
                gradualVolume -> (1 + (targetAlarmVolume - 1) * (elapsed / 30_000f)).toInt().coerceIn(0, targetAlarmVolume)
                else -> targetAlarmVolume
            }
            runCatching { manager.setStreamVolume(AudioManager.STREAM_ALARM, next, 0) }
            volumeHandler.postDelayed(this, 1_000L)
        }
    }

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
            intent.getBooleanExtra("delete_after_ringing", false),
            intent.getIntExtra("walk_target", 40),
            intent.getStringExtra("shake_difficulty") ?: "MEDIUM",
            intent.getStringExtra("type_mode") ?: "RANDOM",
            intent.getStringExtra("custom_phrase").orEmpty(),
            intent.getIntExtra("touch_target", 30),
            intent.getIntExtra("pattern_size", 3)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(id, notification)
        startPlayback(uri, vibrate)
        // Besides the full-screen notification, try opening the ring screen directly.
        // Android may refuse this from the background; the notification covers that case.
        runCatching {
            startActivity(Intent(this, AlarmRingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("alarm_id", id)
                putExtra("label", label)
                putExtra("ringtone_uri", uri)
                putExtra("vibrate", vibrate)
                putExtra("wake_mode", intent.getStringExtra("wake_mode") ?: "NONE")
                putExtra("math_difficulty", intent.getStringExtra("math_difficulty") ?: "MEDIUM")
                putExtra("delete_after_ringing", intent.getBooleanExtra("delete_after_ringing", false))
                putExtra("walk_target", intent.getIntExtra("walk_target", 40))
                putExtra("shake_difficulty", intent.getStringExtra("shake_difficulty") ?: "MEDIUM")
                putExtra("type_mode", intent.getStringExtra("type_mode") ?: "RANDOM")
                putExtra("custom_phrase", intent.getStringExtra("custom_phrase").orEmpty())
                putExtra("touch_target", intent.getIntExtra("touch_target", 30))
                putExtra("pattern_size", intent.getIntExtra("pattern_size", 3))
            })
        }
        return START_NOT_STICKY
    }

    private fun startPlayback(uriString: String, shouldVibrate: Boolean) {
        stopPlayback()
        if (uriString != "silent") {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val preferences = getSharedPreferences("alarm_preferences", Context.MODE_PRIVATE)
            val maxAlarmVolume = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
            originalAlarmVolume = audioManager!!.getStreamVolume(AudioManager.STREAM_ALARM)
            targetAlarmVolume = (maxAlarmVolume * preferences.getInt("volume", 75) / 100f).toInt().coerceIn(0, maxAlarmVolume)
            gradualVolume = preferences.getBoolean("gradual_volume", true)
            loudEscalation = preferences.getBoolean("loud_escalation", false)
            volumeStartedAt = System.currentTimeMillis()
            volumeHandler.removeCallbacks(volumeTick)
            if (gradualVolume || loudEscalation) {
                runCatching { audioManager!!.setStreamVolume(AudioManager.STREAM_ALARM, if (gradualVolume) 1.coerceAtMost(maxAlarmVolume) else targetAlarmVolume, 0) }
                volumeHandler.post(volumeTick)
            } else {
                runCatching { audioManager!!.setStreamVolume(AudioManager.STREAM_ALARM, targetAlarmVolume, 0) }
            }
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
        volumeHandler.removeCallbacks(volumeTick)
        originalAlarmVolume?.let { previous ->
            runCatching { audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, previous, 0) }
        }
        originalAlarmVolume = null
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
