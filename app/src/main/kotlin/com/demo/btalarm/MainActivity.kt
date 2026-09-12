package com.demo.btalarm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.demo.btalarm.alarm.Alarm
import com.demo.btalarm.alarm.AlarmRepository
import com.demo.btalarm.system.Health
import com.demo.btalarm.ui.AlarmEditScreen
import com.demo.btalarm.ui.AlarmListScreen
import com.demo.btalarm.ui.BtAlarmTheme
import com.demo.btalarm.ui.HealthScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmRepository.init(this)
        setContent {
            BtAlarmTheme {
                AppRoot()
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<Alarm?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Health.hasNotificationPermission(context)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (editorOpen) {
        AlarmEditScreen(
            initial = editorTarget,
            onClose = {
                editorOpen = false
                editorTarget = null
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (tab == 0) stringResource(R.string.tab_alarms)
                        else stringResource(R.string.health_title),
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Alarm, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_alarms)) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Security, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_health)) },
                )
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editorTarget = null
                        editorOpen = true
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_alarm)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> AlarmListScreen(
                    onEdit = {
                        editorTarget = it
                        editorOpen = true
                    },
                )
                else -> HealthScreen()
            }
        }
    }
}
