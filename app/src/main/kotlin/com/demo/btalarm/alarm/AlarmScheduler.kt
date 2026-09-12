package com.demo.btalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.demo.btalarm.MainActivity

/**
 * 闹钟注册。核心是 [AlarmManager.setAlarmClock]：
 * 它是系统唯一把应用视为「用户可见闹钟」的接口 —— 不受 Doze / 应用待机影响，
 * 能唤醒设备，并且会让系统给应用一段前台服务启动豁免窗口。
 */
object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    /** 看门狗自检周期。Doze 下 setAndAllowWhileIdle 最快约 9 分钟一次，15 分钟留足余量。 */
    private const val WATCHDOG_INTERVAL_MS = 15 * 60 * 1000L

    /** 刚刚触发过的闹钟在这个窗口内不重新排期，否则会把正在响的闹钟改到明天。 */
    private const val FIRING_GRACE_MS = 2 * 60 * 1000L

    /** 和系统 nextAlarmClock 比对时的容差，避免因为取整反复重排。 */
    private const val CLOCK_TOLERANCE_MS = 60 * 1000L

    private const val WATCHDOG_REQUEST = 0x7F000001

    fun schedule(context: Context, alarm: Alarm) {
        val at = alarm.nextTriggerAt(System.currentTimeMillis())
        if (at == null) {
            cancel(context, alarm)
            return
        }
        scheduleAt(context, alarm, at)
    }

    fun scheduleAt(context: Context, alarm: Alarm, triggerAt: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val fire = firePendingIntent(context, alarm.id)
        val show = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            MainActivity.intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val info = AlarmManager.AlarmClockInfo(triggerAt, show)
        try {
            manager.setAlarmClock(info, fire)
        } catch (e: SecurityException) {
            // 没有精确闹钟权限（Android 12/13 未授权）。退化为可在 Doze 下唤醒的不精确闹钟，
            // 总比完全不响强；健康页会提示用户去授权。
            Log.w(TAG, "setAlarmClock denied, falling back to inexact", e)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, fire)
        }
        AlarmRepository.setScheduledAt(alarm.id, triggerAt)
    }

    fun cancel(context: Context, alarm: Alarm) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val fire = firePendingIntent(context, alarm.id)
        manager.cancel(fire)
        fire.cancel()
        AlarmRepository.clearScheduledAt(alarm.id)
    }

    fun cancelAll(context: Context) {
        AlarmRepository.alarms.value.forEach { cancel(context, it) }
    }

    /**
     * 自愈入口：开机、时区/时间变更、权限恢复、应用冷启动，以及看门狗每 15 分钟都会走一遍。
     *
     * 只补排「确实漏响」和「压根没排上」的闹钟，不动还在正常等待中的 ——
     * 否则每 15 分钟一次的无条件重排会把用户刚设的「稍后提醒」覆盖掉。
     */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        val lostId = lostRegistrationId(context, now)

        AlarmRepository.alarms.value.forEach { alarm ->
            val expected = AlarmRepository.scheduledAt(alarm.id)

            when {
                // 正在响铃
                expected != null && now - expected in 0..FIRING_GRACE_MS -> Unit

                // 从未排上 / 早该响却没响 / 登记被系统丢掉
                expected == null || expected <= now || alarm.id == lostId -> {
                    if (expected != null && expected <= now) {
                        Log.w(TAG, "alarm ${alarm.id} was due at $expected but never fired, re-arming")
                    }
                    if (alarm.id == lostId) {
                        Log.w(TAG, "alarm ${alarm.id} is missing from the system alarm clock, re-arming")
                    }
                    cancel(context, alarm)
                    if (alarm.enabled) schedule(context, alarm)
                }

                // 正常等待中
                else -> Unit
            }
        }
        ensureWatchdog(context)
    }

    /**
     * 找出「本地以为排上了、系统那边其实没有」的那个闹钟。
     *
     * 只检查最早的那个：系统登记的下一个闹钟一定 <= 我们自己最早的那个。
     * 应用被强行停止时系统会清掉全部登记，而本地记录的时间还在未来，
     * 单靠 scheduledAt 看不出问题，只有这里能发现。
     */
    private fun lostRegistrationId(context: Context, now: Long): Long? {
        // 没有精确闹钟权限时走的是 setAndAllowWhileIdle，本就不会出现在 nextAlarmClock 里
        if (!canScheduleExact(context)) return null

        val pending = AlarmRepository.alarms.value
            .filter { it.enabled }
            .mapNotNull { alarm -> AlarmRepository.scheduledAt(alarm.id)?.let { alarm.id to it } }
            .filter { (_, at) -> at > now }
            .minByOrNull { it.second }
            ?: return null

        val systemNext = context.getSystemService(AlarmManager::class.java)
            ?.nextAlarmClock?.triggerTime
            ?: return pending.first

        return if (systemNext > pending.second + CLOCK_TOLERANCE_MS) pending.first else null
    }

    /** 保存后立即生效：取消旧排期、写入仓库、按需重新注册。 */
    fun apply(context: Context, alarm: Alarm) {
        cancel(context, alarm)
        AlarmRepository.upsert(alarm)
        if (alarm.enabled) schedule(context, alarm)
    }

    fun setEnabled(context: Context, alarm: Alarm, enabled: Boolean) {
        apply(context, alarm.copy(enabled = enabled))
    }

    fun remove(context: Context, id: Long) {
        AlarmRepository.get(id)?.let { cancel(context, it) }
        AlarmRepository.delete(id)
    }

    fun ensureWatchdog(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, watchdogPendingIntent(context))
    }

    fun cancelWatchdog(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(watchdogPendingIntent(context))
    }

    /** 距离下一次响铃还有多久；没有已启用的闹钟时返回 null。 */
    fun nextTriggerTime(): Long? {
        val now = System.currentTimeMillis()
        return AlarmRepository.alarms.value
            .filter { it.enabled }
            .mapNotNull { it.nextTriggerAt(now) }
            .minOrNull()
    }

    /** 该闹钟是否能在 Android 12+ 上拿到精确闹钟权限。 */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return true
        return manager.canScheduleExactAlarms()
    }

    private fun firePendingIntent(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id.toInt(),
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_FIRE)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun watchdogPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            WATCHDOG_REQUEST,
            Intent(context, WatchdogReceiver::class.java).setAction(WatchdogReceiver.ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
