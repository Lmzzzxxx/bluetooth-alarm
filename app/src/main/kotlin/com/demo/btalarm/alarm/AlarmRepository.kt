package com.demo.btalarm.alarm

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * 闹钟持久化。使用 device-protected 存储，这样重启后、用户还没解锁屏幕时
 * （LOCKED_BOOT_COMPLETED）也能读回闹钟并重新注册。
 */
object AlarmRepository {
    private const val PREFS = "bt_alarm"
    private const val KEY_ALARMS = "alarms"
    private const val KEY_NEXT_ID = "next_id"
    private const val KEY_KEEPALIVE = "keepalive"

    private lateinit var appContext: Context

    private val prefs: SharedPreferences by lazy {
        appContext.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _keepAlive = MutableStateFlow(false)
    val keepAlive: StateFlow<Boolean> = _keepAlive.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        _alarms.value = read()
        _keepAlive.value = prefs.getBoolean(KEY_KEEPALIVE, false)
    }

    private fun read(): List<Alarm> {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { Alarm.fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList()).sortedWith(COMPARATOR)
    }

    private fun write(list: List<Alarm>) {
        val sorted = list.sortedWith(COMPARATOR)
        val array = JSONArray()
        sorted.forEach { array.put(it.toJson()) }
        prefs.edit { putString(KEY_ALARMS, array.toString()) }
        _alarms.value = sorted
    }

    fun get(id: Long): Alarm? = _alarms.value.firstOrNull { it.id == id }

    fun nextId(): Long {
        val id = prefs.getLong(KEY_NEXT_ID, 1L)
        prefs.edit { putLong(KEY_NEXT_ID, id + 1) }
        return id
    }

    fun upsert(alarm: Alarm) {
        val current = _alarms.value.toMutableList()
        val index = current.indexOfFirst { it.id == alarm.id }
        if (index >= 0) current[index] = alarm else current.add(alarm)
        write(current)
    }

    fun delete(id: Long) {
        write(_alarms.value.filterNot { it.id == id })
        prefs.edit { remove(scheduledKey(id)) }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        val alarm = get(id) ?: return
        upsert(alarm.copy(enabled = enabled))
    }

    fun setKeepAlive(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_KEEPALIVE, enabled) }
        _keepAlive.value = enabled
    }

    // --- 已排定时刻，用于看门狗自检 ---

    private fun scheduledKey(id: Long) = "scheduled_$id"

    fun scheduledAt(id: Long): Long? =
        prefs.getLong(scheduledKey(id), -1L).takeIf { it > 0 }

    fun setScheduledAt(id: Long, at: Long) {
        prefs.edit { putLong(scheduledKey(id), at) }
    }

    fun clearScheduledAt(id: Long) {
        prefs.edit { remove(scheduledKey(id)) }
    }

    private val COMPARATOR = compareBy<Alarm>({ it.hour }, { it.minute }, { it.id })
}
