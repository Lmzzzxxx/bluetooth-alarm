package com.demo.btalarm.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.demo.btalarm.R
import com.demo.btalarm.alarm.Alarm
import com.demo.btalarm.alarm.AlarmRepository
import com.demo.btalarm.alarm.AlarmScheduler
import com.demo.btalarm.system.Health
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun AlarmListScreen(onEdit: (Alarm) -> Unit) {
    val context = LocalContext.current
    val alarms by AlarmRepository.alarms.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val exactAlarmMissing = !Health.hasExactAlarm(context)
    val notificationMissing = !Health.hasNotificationPermission(context)

    if (alarms.isEmpty() && !exactAlarmMissing && !notificationMissing) {
        EmptyState()
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (exactAlarmMissing) {
            item(key = "warn_exact") {
                WarningCard(stringResource(R.string.exact_permission_needed))
            }
        }
        if (notificationMissing) {
            item(key = "warn_notif") {
                WarningCard(stringResource(R.string.notif_permission_needed))
            }
        }

        items(alarms, key = { it.id }) { alarm ->
            AlarmRow(
                alarm = alarm,
                now = now,
                onEdit = { onEdit(alarm) },
                onToggle = { enabled -> AlarmScheduler.setEnabled(context, alarm, enabled) },
            )
        }

        if (alarms.isEmpty()) {
            item(key = "empty") { EmptyState() }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(
            Icons.Filled.Alarm,
            contentDescription = null,
            modifier = Modifier.height(48.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    now: Long,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val dayNames = arrayOf(
        stringResource(R.string.weekday_mon),
        stringResource(R.string.weekday_tue),
        stringResource(R.string.weekday_wed),
        stringResource(R.string.weekday_thu),
        stringResource(R.string.weekday_fri),
        stringResource(R.string.weekday_sat),
        stringResource(R.string.weekday_sun),
    )

    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = alarm.timeText(),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    color = if (alarm.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
                Text(
                    text = buildString {
                        append(
                            alarm.repeatText(
                                dayNames,
                                stringResource(R.string.repeat_once),
                                stringResource(R.string.repeat_everyday),
                            ),
                        )
                        if (alarm.label.isNotBlank()) append("  ${alarm.label}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatTrigger(alarm, now, dayNames),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (alarm.enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.height(14.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = alarm.musicTitle ?: stringResource(R.string.music_default),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                    )
                    if (alarm.bluetoothOnly) {
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.Filled.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.height(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
            )
        }
    }
}

private fun formatTrigger(alarm: Alarm, now: Long, dayNames: Array<String>): String {
    val trigger = alarm.nextTriggerAt(now) ?: return "—"
    val calendar = Calendar.getInstance().apply { timeInMillis = trigger }
    val time = "%02d:%02d".format(
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
    )
    val dayLabel = when {
        isSameDay(calendar, now) -> "今天"
        isSameDay(calendar, tomorrow(now)) -> "明天"
        else -> "周" + dayNames[(calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7]
    }
    return "$dayLabel $time · ${relative(trigger - now)}"
}

private fun relative(diff: Long): String {
    val minutes = (diff / 60_000).coerceAtLeast(0)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 0 -> "${days}天${hours % 24}小时后"
        hours > 0 -> "${hours}小时${minutes % 60}分后"
        else -> "${minutes}分后"
    }
}

private fun tomorrow(now: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

private fun isSameDay(calendar: Calendar, other: Long): Boolean {
    val target = Calendar.getInstance().apply { timeInMillis = other }
    return calendar.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        calendar.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
}
