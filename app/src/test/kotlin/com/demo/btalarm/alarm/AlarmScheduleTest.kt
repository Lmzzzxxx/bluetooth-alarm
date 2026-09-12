package com.demo.btalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/**
 * 闹钟排期是整个应用里最容易出错、又最难在真机上回归的部分，这里用固定日期覆盖。
 * 参照点：2026-01-05 是星期一。
 */
class AlarmScheduleTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun alarm(
        hour: Int,
        minute: Int,
        repeatDays: Int = 0,
        enabled: Boolean = true,
    ) = Alarm(
        id = 1L,
        hour = hour,
        minute = minute,
        enabled = enabled,
        repeatDays = repeatDays,
    )

    @Test
    fun `once alarm later today stays today`() {
        val now = at(2026, 1, 5, 6, 0) // 周一 06:00
        assertEquals(at(2026, 1, 5, 7, 0), alarm(7, 0).nextTriggerAt(now))
    }

    @Test
    fun `once alarm already past rolls to tomorrow`() {
        val now = at(2026, 1, 5, 8, 0) // 周一 08:00
        assertEquals(at(2026, 1, 6, 7, 0), alarm(7, 0).nextTriggerAt(now))
    }

    @Test
    fun `exactly at trigger time advances to the next occurrence`() {
        // 闹钟正在这一刻触发，重排时必须往后走，否则会自己把自己再排到当前时刻
        val now = at(2026, 1, 5, 7, 0)
        assertEquals(at(2026, 1, 6, 7, 0), alarm(7, 0).nextTriggerAt(now))
    }

    @Test
    fun `disabled alarm has no trigger`() {
        assertNull(alarm(7, 0, enabled = false).nextTriggerAt(at(2026, 1, 5, 6, 0)))
    }

    @Test
    fun `weekday alarm picks the next selected weekday`() {
        val monWedFri = Alarm.DAY_MON or Alarm.DAY_WED or Alarm.DAY_FRI

        assertEquals(
            at(2026, 1, 5, 7, 0), // 周一
            alarm(7, 0, monWedFri).nextTriggerAt(at(2026, 1, 5, 6, 0)),
        )
        assertEquals(
            at(2026, 1, 7, 7, 0), // 跳过周二，到周三
            alarm(7, 0, monWedFri).nextTriggerAt(at(2026, 1, 5, 8, 0)),
        )
        assertEquals(
            at(2026, 1, 9, 7, 0), // 跳过周四，到周五
            alarm(7, 0, monWedFri).nextTriggerAt(at(2026, 1, 7, 8, 0)),
        )
        assertEquals(
            at(2026, 1, 12, 7, 0), // 周五响过之后绕回下周一
            alarm(7, 0, monWedFri).nextTriggerAt(at(2026, 1, 9, 8, 0)),
        )
    }

    @Test
    fun `weekend alarm skips the working week`() {
        assertEquals(
            at(2026, 1, 10, 9, 30), // 周一 → 周六
            alarm(9, 30, Alarm.WEEKEND).nextTriggerAt(at(2026, 1, 5, 6, 0)),
        )
        assertEquals(
            at(2026, 1, 11, 9, 30), // 周六 → 周日
            alarm(9, 30, Alarm.WEEKEND).nextTriggerAt(at(2026, 1, 10, 10, 0)),
        )
        assertEquals(
            at(2026, 1, 17, 9, 30), // 周日 → 下周六
            alarm(9, 30, Alarm.WEEKEND).nextTriggerAt(at(2026, 1, 11, 10, 0)),
        )
    }

    @Test
    fun `every day alarm always lands within a day`() {
        val now = at(2026, 1, 5, 23, 30)
        assertEquals(at(2026, 1, 6, 7, 0), alarm(7, 0, Alarm.EVERY_DAY).nextTriggerAt(now))
    }

    @Test
    fun `single day mask behaves like a weekly alarm`() {
        val sundayOnly = Alarm.DAY_SUN
        assertEquals(
            at(2026, 1, 11, 8, 0), // 从周一到周日
            alarm(8, 0, sundayOnly).nextTriggerAt(at(2026, 1, 5, 6, 0)),
        )
        assertEquals(
            at(2026, 1, 18, 8, 0), // 周日当天响过之后，隔一整周
            alarm(8, 0, sundayOnly).nextTriggerAt(at(2026, 1, 11, 9, 0)),
        )
    }

    @Test
    fun `weekday index mapping matches calendar`() {
        // 掩码 bit0=周一 … bit6=周日，和 nextTriggerAt 内部的换算必须一致
        val monday = at(2026, 1, 5, 0, 0)
        val calendar = Calendar.getInstance().apply { timeInMillis = monday }
        assertEquals(Calendar.MONDAY, calendar.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7)

        val sunday = at(2026, 1, 11, 0, 0)
        calendar.timeInMillis = sunday
        assertEquals(Calendar.SUNDAY, calendar.get(Calendar.DAY_OF_WEEK))
        assertEquals(6, (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7)
    }
}
