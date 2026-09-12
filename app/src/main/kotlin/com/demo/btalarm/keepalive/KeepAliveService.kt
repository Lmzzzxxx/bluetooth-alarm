package com.demo.btalarm.keepalive

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.demo.btalarm.MainActivity
import com.demo.btalarm.R
import com.demo.btalarm.system.Notifier

/**
 * 可选的常驻保活服务。默认关闭。
 *
 * 大多数情况下 setAlarmClock + 看门狗已经足够；但在小米/华为/OPPO/vivo 这类
 * 激进清理后台的 ROM 上，一条常驻前台通知能显著降低进程被干掉、进而闹钟被
 * 连带清掉的概率。代价是通知栏常驻一条通知。
 */
class KeepAliveService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        running = true
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat() {
        val content = PendingIntent.getActivity(
            this,
            0,
            MainActivity.intent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, Notifier.CHANNEL_KEEPALIVE)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.keepalive_notification_title))
            .setContentText(getString(R.string.keepalive_notification_text))
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(content)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val TAG = "KeepAliveService"
        private const val NOTIF_ID = 0x5301

        @Volatile
        private var running = false

        val isRunning: Boolean get() = running

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, KeepAliveService::class.java)) }
                .onFailure { Log.e(TAG, "cannot start keep-alive service", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}
