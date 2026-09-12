package com.demo.btalarm.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.demo.btalarm.R
import com.demo.btalarm.alarm.Alarm
import com.demo.btalarm.alarm.AlarmRepository
import com.demo.btalarm.alarm.AlarmScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(initial: Alarm?, onClose: () -> Unit) {
    val context = LocalContext.current
    val isNew = initial == null

    val timeState = rememberTimePickerState(
        initialHour = initial?.hour ?: 7,
        initialMinute = initial?.minute ?: 0,
        is24Hour = true,
    )
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var repeatDays by remember { mutableIntStateOf(initial?.repeatDays ?: 0) }
    var musicUri by remember { mutableStateOf(initial?.musicUri) }
    var musicTitle by remember { mutableStateOf(initial?.musicTitle) }
    var vibrate by remember { mutableStateOf(initial?.vibrate ?: true) }
    var bluetoothOnly by remember { mutableStateOf(initial?.bluetoothOnly ?: false) }
    var snoozeMinutes by remember { mutableIntStateOf(initial?.snoozeMinutes ?: 10) }
    var rampVolume by remember { mutableStateOf(initial?.rampVolume ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }

    val pickMusic = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            // 必须显式接管持久化读权限，否则重启后放不出声音
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            musicUri = uri.toString()
            musicTitle = DocumentFile.fromSingleUri(context, uri)?.name
                ?: uri.lastPathSegment.orEmpty()
        }
    }

    val dayNames = listOf(
        stringResource(R.string.weekday_mon),
        stringResource(R.string.weekday_tue),
        stringResource(R.string.weekday_wed),
        stringResource(R.string.weekday_thu),
        stringResource(R.string.weekday_fri),
        stringResource(R.string.weekday_sat),
        stringResource(R.string.weekday_sun),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isNew) R.string.add_alarm else R.string.edit_alarm,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            TimeInput(state = timeState)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.label_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))

            SectionTitle(stringResource(R.string.repeat))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                dayNames.forEachIndexed { index, name ->
                    FilterChip(
                        selected = repeatDays and (1 shl index) != 0,
                        onClick = { repeatDays = repeatDays xor (1 shl index) },
                        label = { Text(name) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = repeatDays == 0,
                    onClick = { repeatDays = 0 },
                    label = { Text(stringResource(R.string.repeat_once)) },
                )
                FilterChip(
                    selected = repeatDays == Alarm.EVERY_DAY,
                    onClick = { repeatDays = Alarm.EVERY_DAY },
                    label = { Text(stringResource(R.string.repeat_everyday)) },
                )
                FilterChip(
                    selected = repeatDays == Alarm.WORKDAYS,
                    onClick = { repeatDays = Alarm.WORKDAYS },
                    label = { Text(stringResource(R.string.repeat_workdays)) },
                )
                FilterChip(
                    selected = repeatDays == Alarm.WEEKEND,
                    onClick = { repeatDays = Alarm.WEEKEND },
                    label = { Text(stringResource(R.string.repeat_weekend)) },
                )
            }
            Spacer(Modifier.height(24.dp))

            SectionTitle(stringResource(R.string.music))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = musicTitle ?: stringResource(R.string.music_default),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pickMusic.launch(arrayOf("audio/*")) }) {
                            Text(stringResource(R.string.music_pick))
                        }
                        if (musicUri != null) {
                            TextButton(
                                onClick = {
                                    musicUri = null
                                    musicTitle = null
                                },
                            ) {
                                Text(stringResource(R.string.music_default))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            ToggleRow(
                title = stringResource(R.string.bluetooth_only),
                subtitle = stringResource(R.string.bluetooth_only_desc),
                checked = bluetoothOnly,
                onChange = { bluetoothOnly = it },
            )
            ToggleRow(
                title = stringResource(R.string.vibrate),
                checked = vibrate,
                onChange = { vibrate = it },
            )
            ToggleRow(
                title = stringResource(R.string.ramp_volume),
                checked = rampVolume,
                onChange = { rampVolume = it },
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle(stringResource(R.string.snooze_minutes))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 10, 15, 20, 30).forEach { minutes ->
                    FilterChip(
                        selected = snoozeMinutes == minutes,
                        onClick = { snoozeMinutes = minutes },
                        label = { Text("$minutes 分") },
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        val alarm = Alarm(
                            id = initial?.id ?: AlarmRepository.nextId(),
                            hour = timeState.hour,
                            minute = timeState.minute,
                            enabled = initial?.enabled ?: true,
                            label = label.trim(),
                            musicUri = musicUri,
                            musicTitle = musicTitle,
                            repeatDays = repeatDays,
                            vibrate = vibrate,
                            bluetoothOnly = bluetoothOnly,
                            snoozeMinutes = snoozeMinutes,
                            rampVolume = rampVolume,
                        )
                        AlarmScheduler.apply(context, alarm)
                        onClose()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(initial.timeText()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        AlarmScheduler.remove(context, initial.id)
                        confirmDelete = false
                        onClose()
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
