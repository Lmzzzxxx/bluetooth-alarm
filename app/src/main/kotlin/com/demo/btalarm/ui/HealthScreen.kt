package com.demo.btalarm.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demo.btalarm.R
import com.demo.btalarm.alarm.AlarmRepository
import com.demo.btalarm.keepalive.KeepAliveService
import com.demo.btalarm.system.Health

@Composable
fun HealthScreen() {
    val context = LocalContext.current
    val keepAlive by AlarmRepository.keepAlive.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }

    // 从系统设置页返回时重新检查
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val exactAlarm = remember(refresh) { Health.hasExactAlarm(context) }
    val notifications = remember(refresh) { Health.hasNotificationPermission(context) }
    val fullScreen = remember(refresh) { Health.hasFullScreenIntent(context) }
    val battery = remember(refresh) { Health.ignoresBatteryOptimizations(context) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "intro") {
            Text(
                stringResource(R.string.health_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        item(key = "exact") {
            CheckItem(
                title = stringResource(R.string.health_exact_alarm),
                desc = stringResource(R.string.health_exact_alarm_desc),
                ok = exactAlarm,
                onAction = { Health.openExactAlarmSettings(context) },
            )
        }
        item(key = "notif") {
            CheckItem(
                title = stringResource(R.string.health_notification),
                desc = stringResource(R.string.health_notification_desc),
                ok = notifications,
                onAction = { Health.openNotificationSettings(context) },
            )
        }
        item(key = "fullscreen") {
            CheckItem(
                title = stringResource(R.string.health_fullscreen),
                desc = stringResource(R.string.health_fullscreen_desc),
                ok = fullScreen,
                onAction = { Health.openFullScreenIntentSettings(context) },
            )
        }
        item(key = "battery") {
            CheckItem(
                title = stringResource(R.string.health_battery),
                desc = stringResource(R.string.health_battery_desc),
                ok = battery,
                onAction = { Health.requestIgnoreBatteryOptimizations(context) },
            )
        }
        item(key = "autostart") {
            AutostartItem(context)
        }
        item(key = "keepalive") {
            KeepAliveItem(keepAlive) { enabled ->
                AlarmRepository.setKeepAlive(enabled)
                if (enabled) KeepAliveService.start(context) else KeepAliveService.stop(context)
            }
        }
    }
}

@Composable
private fun CheckItem(
    title: String,
    desc: String,
    ok: Boolean,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (!ok) {
                TextButton(onClick = onAction) {
                    Text(stringResource(R.string.health_todo))
                }
            } else {
                Text(
                    stringResource(R.string.health_ok),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun AutostartItem(context: Context) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.health_autostart),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(R.string.health_autostart_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            TextButton(onClick = { Health.openAutostartSettings(context) }) {
                Text(stringResource(R.string.health_todo))
            }
        }
    }
}

@Composable
private fun KeepAliveItem(checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.keepalive_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.keepalive_toggle_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
