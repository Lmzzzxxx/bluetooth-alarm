package com.demo.btalarm.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/** 各项保活/权限检查与跳转。 */
object Health {
    private const val TAG = "Health"

    fun hasExactAlarm(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return true
        return manager.canScheduleExactAlarms()
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        return manager.canUseFullScreenIntent()
    }

    fun ignoresBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return true
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        launch(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context)))
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        launch(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context)))
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        launch(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)))
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        launch(context, intent)
    }

    fun openAppDetails(context: Context) {
        launch(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)),
        )
    }

    /**
     * 打开厂商的自启动管理页。识别不到就退回应用详情页，让用户自己找。
     * @return 是否命中了已知的厂商页面
     */
    fun openAutostartSettings(context: Context): Boolean {
        for ((packageName, className) in AUTOSTART_SCREENS) {
            val component = ComponentName(packageName, className)
            val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) == null) continue
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        Log.i(TAG, "no known autostart screen, opening app details")
        openAppDetails(context)
        return false
    }

    private fun packageUri(context: Context) = "package:${context.packageName}".toUri()

    private fun launch(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Log.w(TAG, "cannot open settings: ${intent.action}", it) }
    }

    private val AUTOSTART_SCREENS = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
        "com.samsung.android.sm_cn" to "com.samsung.android.sm_cn.ui.battery.BatteryActivity",
        "com.meizu.safe" to "com.meizu.safe.permission.SmartBGActivity",
        "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
        "com.asus.mobilemanager" to "com.asus.mobilemanager.powersaver.PowerSaverSettings",
        "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
    )
}
