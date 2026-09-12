package com.demo.btalarm.ring

import android.content.Context
import com.demo.btalarm.alarm.AlarmRepository
import com.demo.btalarm.alarm.AlarmScheduler

/** 响铃时的两个出口动作，通知栏按钮和响铃界面共用同一套逻辑。 */
object RingActions {

    fun dismiss(context: Context) {
        AlarmRingService.stop(context)
    }

    fun snooze(context: Context, alarmId: Long) {
        val alarm = AlarmRepository.get(alarmId)
        if (alarm != null) {
            // 单次闹钟响过之后会被自动停用。要让它稍后还能再响，得先重新启用，
            // 否则稍后提醒到点时 AlarmReceiver 会因为 enabled=false 直接跳过。
            if (!alarm.enabled) AlarmRepository.setEnabled(alarmId, true)

            val at = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
            AlarmScheduler.scheduleAt(context, alarm, at)
        }
        AlarmRingService.stop(context)
    }
}
