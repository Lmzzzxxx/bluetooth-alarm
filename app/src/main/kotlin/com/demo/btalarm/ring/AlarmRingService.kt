package com.demo.btalarm.ring

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.demo.btalarm.R
import com.demo.btalarm.alarm.Alarm
import com.demo.btalarm.alarm.AlarmReceiver
import com.demo.btalarm.alarm.AlarmRepository
import com.demo.btalarm.system.Notifier

/**
 * 响铃前台服务。以前台服务（mediaPlayback）持有播放过程，
 * 保证响铃期间不会被系统回收，同时持有唤醒锁避免设备在响铃中途睡回去。
 */
class AlarmRingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: AlarmPlayer
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var autoStop: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        player = AlarmPlayer(this)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        beginRinging(alarmId)
        return START_NOT_STICKY
    }

    private fun beginRinging(alarmId: Long) {
        val alarm = AlarmRepository.get(alarmId)
        if (alarm == null) {
            // 闹钟已被删除；仍然要 startForeground，否则系统会判定 ANR
            startForegroundCompat(placeholderNotification())
            stopSelf()
            return
        }

        acquireWakeLock()

        val bluetooth = AudioRoute.findBluetoothOutput(this)
        val silent = alarm.bluetoothOnly && bluetooth == null

        var state = RingState(
            alarmId = alarm.id,
            time = alarm.timeText(),
            label = alarm.label,
            bluetoothName = bluetooth?.name,
            silentBecauseNoBluetooth = silent,
            usedFallbackMusic = false,
        )
        RingStateHolder.update(state)

        startForegroundCompat(buildNotification(alarm, state))

        if (!silent) {
            player.play(alarm, bluetooth?.device)
            if (player.usedFallback) {
                state = state.copy(usedFallbackMusic = true)
                RingStateHolder.update(state)
                notify(buildNotification(alarm, state))
            }
            if (alarm.vibrate) startVibrating()
        }

        launchRingUi()

        autoStop = Runnable { stopSelf() }.also { handler.postDelayed(it, AUTO_STOP_MS) }
    }

    override fun onDestroy() {
        autoStop?.let { handler.removeCallbacks(it) }
        autoStop = null

        player.stop()
        stopVibrating()
        releaseWakeLock()
        RingStateHolder.update(null)
        running = false

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- 前台通知 ---

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(android.app.NotificationManager::class.java) ?: return
        manager.notify(NOTIF_ID, notification)
    }

    private fun placeholderNotification(): Notification =
        Notification.Builder(this, Notifier.CHANNEL_RING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.ring_notification_title))
            .setOngoing(true)
            .build()

    private fun buildNotification(alarm: Alarm, state: RingState): Notification {
        val fullScreen = PendingIntent.getActivity(
            this,
            REQ_CONTENT,
            RingActivity.intent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = when {
            state.silentBecauseNoBluetooth -> getString(R.string.ring_silent_no_bt)
            state.bluetoothName != null -> getString(R.string.ring_via_bluetooth, state.bluetoothName)
            else -> getString(R.string.ring_via_speaker)
        }

        val snooze = PendingIntent.getBroadcast(
            this,
            REQ_SNOOZE,
            AlarmReceiver.snoozeIntent(this, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getBroadcast(
            this,
            REQ_DISMISS,
            AlarmReceiver.dismissIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, Notifier.CHANNEL_RING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(alarm.timeText() + if (alarm.label.isNotBlank()) "  ${alarm.label}" else "")
            .setContentText(text)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(Notification.Action.Builder(null, getString(R.string.ring_snooze), snooze).build())
            .addAction(Notification.Action.Builder(null, getString(R.string.ring_dismiss), dismiss).build())
            .build()
    }

    /**
     * 直接拉起响铃界面。运行中的前台服务让应用处于「前台」状态，可以启动 Activity；
     * 若被系统拦下（例如锁屏且未授予全屏通知权限），通知上的 fullScreenIntent 会兜底。
     */
    private fun launchRingUi() {
        runCatching {
            startActivity(RingActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Log.w(TAG, "direct launch blocked, falling back to full-screen intent", it)
        }
    }

    // --- 唤醒锁 / 震动 ---

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(AUTO_STOP_MS + 60_000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    private fun startVibrating() {
        val device = resolveVibrator() ?: return
        vibrator = device
        runCatching {
            device.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
        }
    }

    private fun stopVibrating() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun resolveVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    companion object {
        private const val TAG = "AlarmRingService"

        const val ACTION_START = "com.demo.btalarm.ring.START"
        const val ACTION_STOP = "com.demo.btalarm.ring.STOP"
        const val EXTRA_ALARM_ID = "alarm_id"

        private const val NOTIF_ID = 0x5201
        private const val REQ_CONTENT = 0x5202
        private const val REQ_SNOOZE = 0x5203
        private const val REQ_DISMISS = 0x5204
        private const val WAKELOCK_TAG = "btalarm:ringing"
        private const val AUTO_STOP_MS = 15 * 60 * 1000L
        private val VIBRATION_PATTERN = longArrayOf(0, 800, 800)

        @Volatile
        private var running = false

        val isRunning: Boolean get() = running

        fun start(context: Context, alarmId: Long) {
            val intent = Intent(context, AlarmRingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ALARM_ID, alarmId)
            runCatching { context.startForegroundService(intent) }
                .onFailure { Log.e(TAG, "cannot start ring service", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmRingService::class.java))
        }
    }
}
