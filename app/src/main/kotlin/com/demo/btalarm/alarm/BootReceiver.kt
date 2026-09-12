package com.demo.btalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.demo.btalarm.keepalive.KeepAliveService

/**
 * 开机 / 应用更新 / 时间或时区变更后重新注册闹钟。
 * 声明为 directBootAware，所以用户在重启后还没解锁时（LOCKED_BOOT_COMPLETED）
 * 闹钟就已经恢复，不会出现「重启后整晚不响」。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive ${intent.action}")
        AlarmRepository.init(context)
        AlarmScheduler.rescheduleAll(context)
        if (AlarmRepository.keepAlive.value) {
            KeepAliveService.start(context)
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
