package com.demo.btalarm.ring

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

data class BluetoothOutput(val device: AudioDeviceInfo, val name: String)

/**
 * 用 AudioManager 枚举当前已连接的音频输出设备来判断蓝牙耳机是否在线。
 *
 * 相比 BluetoothAdapter.getProfileProxy(A2DP)，这条路不需要 BLUETOOTH_CONNECT 权限，
 * 而且拿到的是可直接交给 MediaPlayer.setPreferredDevice() 的 AudioDeviceInfo。
 */
object AudioRoute {

    fun findBluetoothOutput(context: Context): BluetoothOutput? {
        val manager = context.getSystemService(AudioManager::class.java) ?: return null
        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val supportsBle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val supportsHearingAid = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        val device = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            ?: outputs.firstOrNull { supportsBle && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
            ?: outputs.firstOrNull { supportsBle && it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER }
            ?: outputs.firstOrNull { supportsHearingAid && it.type == AudioDeviceInfo.TYPE_HEARING_AID }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            ?: return null

        return BluetoothOutput(device, displayName(device))
    }

    private fun displayName(device: AudioDeviceInfo): String {
        val product = device.productName?.toString()?.trim().orEmpty()
        return product.ifEmpty { "蓝牙设备" }
    }
}
