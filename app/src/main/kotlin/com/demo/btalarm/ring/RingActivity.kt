package com.demo.btalarm.ring

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demo.btalarm.ui.RingScreen
import kotlinx.coroutines.delay

/** 响铃界面：锁屏之上全屏展示，独立任务栈。 */
class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        setContent {
            val state by RingStateHolder.state.collectAsStateWithLifecycle()
            var everActive by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(state) {
                when {
                    state != null -> everActive = true
                    everActive -> finish()
                    else -> {
                        // 可能是服务还没写好状态，也可能服务已经不在了
                        delay(3000)
                        if (RingStateHolder.state.value == null) finish()
                    }
                }
            }

            RingScreen(
                state = state,
                onSnooze = { state?.let { RingActions.snooze(this, it.alarmId) } },
                onDismiss = { RingActions.dismiss(this) },
            )
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, RingActivity::class.java)
    }
}
