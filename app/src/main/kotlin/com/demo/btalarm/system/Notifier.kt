package com.demo.btalarm.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.demo.btalarm.R

object Notifier {
    const val CHANNEL_RING = "ring"
    const val CHANNEL_KEEPALIVE = "keepalive"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val ring = NotificationChannel(
            CHANNEL_RING,
            context.getString(R.string.ring_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            // 声音由 AlarmPlayer 播放，渠道再出声会变成双重播放
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val keepAlive = NotificationChannel(
            CHANNEL_KEEPALIVE,
            context.getString(R.string.keepalive_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(ring, keepAlive))
    }
}
