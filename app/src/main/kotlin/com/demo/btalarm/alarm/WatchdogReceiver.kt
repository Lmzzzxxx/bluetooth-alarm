package com.demo.btalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.demo.btalarm.keepalive.KeepAliveService

/**
 * 看门狗：每 15 分钟自检一次，把被系统或厂商 ROM 悄悄丢掉的闹钟重新排上，
 * 并在常驻保活开启时确认守护服务还活着。
 *
 * 注意这里不依赖 AlarmManager.getNextAlarmClock() 做校验 —— 那个接口返回的是
 * 全系统（含系统时钟应用）的下一个闹钟，无法区分是不是本应用的。
 */
class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TICK) return
        AlarmRepository.init(context)
        AlarmScheduler.rescheduleAll(context)

        if (AlarmRepository.keepAlive.value && !KeepAliveService.isRunning) {
            Log.w(TAG, "keep-alive service is gone, restarting")
            KeepAliveService.start(context)
        }
    }

    companion object {
        private const val TAG = "WatchdogReceiver"
        const val ACTION_TICK = "com.demo.btalarm.action.WATCHDOG_TICK"
    }
}
