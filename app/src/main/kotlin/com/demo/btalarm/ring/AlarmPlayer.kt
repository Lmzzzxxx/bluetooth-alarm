package com.demo.btalarm.ring

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import com.demo.btalarm.alarm.Alarm

/**
 * 闹铃播放器。
 *
 * 关键点：必须用 USAGE_MEDIA 而不是 USAGE_ALARM。
 * Android 会把 USAGE_ALARM 强制路由到扬声器（闹钟设计上不允许走蓝牙），
 * 想从蓝牙耳机出声就只能声明成媒体流，再用 setPreferredDevice 指定设备。
 */
class AlarmPlayer(private val context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var rampStep: Runnable? = null
    private var savedMediaVolume: Int? = null

    /** 选定的音乐文件打不开时置为 true，表示已回退到系统默认闹铃。 */
    var usedFallback = false
        private set

    fun play(alarm: Alarm, preferred: AudioDeviceInfo?) {
        stop()
        usedFallback = false

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        requestFocus(attributes)
        raiseStreamVolume()

        val chosen = alarm.musicUri?.toUri()
        val started = chosen != null && tryStart(chosen, attributes, preferred, alarm)

        if (!started) {
            usedFallback = chosen != null
            val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (fallback == null) {
                Log.e(TAG, "no usable audio source at all")
                return
            }
            tryStart(fallback, attributes, preferred, alarm)
        }
    }

    private fun tryStart(
        source: Uri,
        attributes: AudioAttributes,
        preferred: AudioDeviceInfo?,
        alarm: Alarm,
    ): Boolean {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(attributes)
            mp.setDataSource(context, source)
            mp.isLooping = true
            mp.prepare()
            // setPreferredDevice 是 API 28 才有的。26/27 上不做强制路由 ——
            // 已连接 A2DP 时媒体流本来就会自动走蓝牙。
            if (preferred != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mp.preferredDevice = preferred
            }
            mp.setVolume(if (alarm.rampVolume) RAMP_START else 1f, if (alarm.rampVolume) RAMP_START else 1f)
            mp.start()
            player = mp
            if (alarm.rampVolume) startRamp()
            true
        } catch (e: Exception) {
            Log.w(TAG, "cannot play $source", e)
            runCatching { mp.release() }
            false
        }
    }

    private fun startRamp() {
        var level = RAMP_START
        val step = object : Runnable {
            override fun run() {
                level = (level + RAMP_DELTA).coerceAtMost(1f)
                player?.setVolume(level, level)
                if (level < 1f) handler.postDelayed(this, RAMP_STEP_MS)
            }
        }
        rampStep = step
        handler.postDelayed(step, RAMP_STEP_MS)
    }

    fun stop() {
        rampStep?.let { handler.removeCallbacks(it) }
        rampStep = null

        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            runCatching { it.release() }
        }
        player = null

        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null

        restoreStreamVolume()
    }

    private fun requestFocus(attributes: AudioAttributes) {
        val manager = audioManager ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = request
        manager.requestAudioFocus(request)
    }

    /**
     * 软件增益（setVolume）不受路由影响，但前提是媒体流本身没被调到 0。
     * 这里把媒体音量抬到至少 80%，停止时还原。
     */
    private fun raiseStreamVolume() {
        val manager = audioManager ?: return
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val target = (max * 0.8f).toInt().coerceAtLeast(1)
        if (current < target) {
            savedMediaVolume = current
            runCatching { manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
                .onFailure { Log.w(TAG, "cannot raise stream volume", it) }
        }
    }

    private fun restoreStreamVolume() {
        val manager = audioManager ?: return
        val saved = savedMediaVolume ?: return
        savedMediaVolume = null
        runCatching { manager.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0) }
    }

    private companion object {
        const val TAG = "AlarmPlayer"
        const val RAMP_START = 0.25f
        const val RAMP_DELTA = 0.05f
        const val RAMP_STEP_MS = 1500L
    }
}
