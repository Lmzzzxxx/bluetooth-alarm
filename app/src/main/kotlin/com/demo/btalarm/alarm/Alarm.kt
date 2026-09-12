package com.demo.btalarm.alarm

import org.json.JSONObject
import java.util.Calendar

/**
 * repeatDays 是星期位掩码：bit0=周一 … bit6=周日。0 表示「仅一次」。
 */
data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val label: String = "",
    val musicUri: String? = null,
    val musicTitle: String? = null,
    val repeatDays: Int = 0,
    val vibrate: Boolean = true,
    val bluetoothOnly: Boolean = false,
    val snoozeMinutes: Int = 10,
    val rampVolume: Boolean = true,
) {
    val repeating: Boolean get() = repeatDays != 0

    fun timeText(): String = "%02d:%02d".format(hour, minute)

    /**
     * 下一次触发时刻（epoch millis）。未启用、或重复日掩码为空时返回 null。
     * 跨夏令时时逐日重设时分，避免偏移。
     */
    fun nextTriggerAt(from: Long): Long? {
        if (!enabled) return null

        val cal = Calendar.getInstance()
        cal.timeInMillis = from
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        if (cal.timeInMillis <= from) cal.add(Calendar.DAY_OF_MONTH, 1)

        if (!repeating) return cal.timeInMillis

        repeat(8) {
            val weekdayIndex = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 周一=0 … 周日=6
            if (repeatDays and (1 shl weekdayIndex) != 0) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
        }
        return null
    }

    fun repeatText(dayNames: Array<String>, once: String, everyday: String): String = when (repeatDays) {
        0 -> once
        EVERY_DAY -> everyday
        else -> (0..6).filter { repeatDays and (1 shl it) != 0 }.joinToString(" ") { dayNames[it] }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("enabled", enabled)
        put("label", label)
        put("musicUri", musicUri ?: "")
        put("musicTitle", musicTitle ?: "")
        put("repeatDays", repeatDays)
        put("vibrate", vibrate)
        put("bluetoothOnly", bluetoothOnly)
        put("snoozeMinutes", snoozeMinutes)
        put("rampVolume", rampVolume)
    }

    companion object {
        const val DAY_MON = 1 shl 0
        const val DAY_TUE = 1 shl 1
        const val DAY_WED = 1 shl 2
        const val DAY_THU = 1 shl 3
        const val DAY_FRI = 1 shl 4
        const val DAY_SAT = 1 shl 5
        const val DAY_SUN = 1 shl 6

        const val EVERY_DAY = 0b1111111
        const val WORKDAYS = 0b0011111
        const val WEEKEND = 0b1100000

        fun fromJson(json: JSONObject): Alarm = Alarm(
            id = json.getLong("id"),
            hour = json.getInt("hour"),
            minute = json.getInt("minute"),
            enabled = json.optBoolean("enabled", true),
            label = json.optString("label"),
            musicUri = json.optString("musicUri").ifEmpty { null },
            musicTitle = json.optString("musicTitle").ifEmpty { null },
            repeatDays = json.optInt("repeatDays", 0),
            vibrate = json.optBoolean("vibrate", true),
            bluetoothOnly = json.optBoolean("bluetoothOnly", false),
            snoozeMinutes = json.optInt("snoozeMinutes", 10),
            rampVolume = json.optBoolean("rampVolume", true),
        )
    }
}
