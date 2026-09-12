package com.demo.btalarm

import android.app.Application
import com.demo.btalarm.alarm.AlarmRepository
import com.demo.btalarm.alarm.AlarmScheduler
import com.demo.btalarm.keepalive.KeepAliveService
import com.demo.btalarm.system.Notifier

class AlarmApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AlarmRepository.init(this)
        Notifier.createChannels(this)

        // 应用每次冷启动都重排一遍并重启看门狗：这是用户能主动触发的最强自愈时机，
        // 可以修掉被厂商 ROM 清理掉的排期。
        AlarmScheduler.rescheduleAll(this)
        if (AlarmRepository.keepAlive.value) {
            KeepAliveService.start(this)
        }
    }
}
