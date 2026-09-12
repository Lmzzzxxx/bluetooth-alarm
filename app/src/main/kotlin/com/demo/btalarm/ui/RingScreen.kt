package com.demo.btalarm.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demo.btalarm.R
import com.demo.btalarm.ring.RingState

private val RingBackground = Color(0xFF0A0F14)
private val RingAccent = Color(0xFF8ECDFF)

/** 响铃界面。始终使用深色，保证锁屏上醒目。 */
@Composable
fun RingScreen(
    state: RingState?,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "ring")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RingBackground)
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))

            Text(
                text = state?.time ?: "--:--",
                fontSize = 88.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                modifier = Modifier.graphicsLayer { this.alpha = alpha },
            )

            if (!state?.label.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = RingAccent,
                )
            }

            Spacer(Modifier.height(28.dp))
            RouteIndicator(state)

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Text(stringResource(R.string.ring_snooze), fontSize = 17.sp)
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                ) {
                    Text(stringResource(R.string.ring_dismiss), fontSize = 17.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RouteIndicator(state: RingState?) {
    val (icon, text, tint) = when {
        state == null -> Triple(Icons.Filled.Speaker, "", Color.White)
        state.silentBecauseNoBluetooth -> Triple(
            Icons.AutoMirrored.Filled.VolumeOff,
            stringResource(R.string.ring_silent_no_bt),
            Color(0xFFFFB4AB),
        )
        state.bluetoothName != null -> Triple(
            Icons.Filled.Bluetooth,
            stringResource(R.string.ring_via_bluetooth, state.bluetoothName),
            RingAccent,
        )
        else -> Triple(
            Icons.Filled.Speaker,
            stringResource(R.string.ring_via_speaker),
            Color(0xFFCAC4D0),
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(8.dp))
            Text(text, color = tint, style = MaterialTheme.typography.bodyLarge)
        }
        if (state?.usedFallbackMusic == true) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.ring_music_missing),
                color = Color(0xFFFFB4AB),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
