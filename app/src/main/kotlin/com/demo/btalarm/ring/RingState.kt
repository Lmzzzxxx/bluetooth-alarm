package com.demo.btalarm.ring

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 响铃服务向响铃界面暴露的状态。null 表示当前没有在响铃。 */
data class RingState(
    val alarmId: Long,
    val time: String,
    val label: String,
    val bluetoothName: String?,
    val silentBecauseNoBluetooth: Boolean,
    val usedFallbackMusic: Boolean,
)

object RingStateHolder {
    private val _state = MutableStateFlow<RingState?>(null)
    val state: StateFlow<RingState?> = _state.asStateFlow()

    fun update(value: RingState?) {
        _state.value = value
    }
}
