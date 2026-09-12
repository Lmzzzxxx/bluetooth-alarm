package com.demo.btalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.demo.btalarm.ring.AlarmRingService
import com.demo.btalarm.ring.RingActions

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AlarmRepository.init(context)
        val id = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        Log.i(TAG, "onReceive action=${intent.action} id=$id")

        when (intent.action) {
            ACTION_FIRE -> onFire(context, id)
            ACTION_SNOOZE -> RingActions.snooze(context, id)
            ACTION_DISMISS -> RingActions.dismiss(context)
        }
    }

    private fun onFire(context: Context, id: Long) {
        val alarm = AlarmRepository.get(id) ?: return
        if (!alarm.enabled) return

        AlarmRepository.clearScheduledAt(id)

        // 由 setAlarmClock 触发的广播自带前台服务启动豁免，这里可以安全地拉起响铃服务。
        AlarmRingService.start(context, id)

        if (alarm.repeating) {
            schedule(context, alarm)
        } else {
            AlarmRepository.setEnabled(id, false)
        }
    }

    private fun schedule(context: Context, alarm: Alarm) {
        val next = alarm.nextTriggerAt(System.currentTimeMillis())
        if (next != null) {
            AlarmScheduler.scheduleAt(context, alarm, next)
        } else {
            AlarmScheduler.cancel(context, alarm)
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"

        const val ACTION_FIRE = "com.demo.btalarm.action.FIRE"
        const val ACTION_SNOOZE = "com.demo.btalarm.action.SNOOZE"
        const val ACTION_DISMISS = "com.demo.btalarm.action.DISMISS"
        const val EXTRA_ALARM_ID = "alarm_id"

        fun snoozeIntent(context: Context, id: Long): Intent =
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_ALARM_ID, id)

        fun dismissIntent(context: Context): Intent =
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_DISMISS)
    }
}
