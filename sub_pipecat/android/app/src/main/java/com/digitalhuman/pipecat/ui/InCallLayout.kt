package com.digitalhuman.pipecat.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Status indicator showing who is currently speaking.
 */
@Composable
fun SpeakingIndicator(
    label: String,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = if (isSpeaking) Color.Red else Color.Gray,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Simulated audio amplitude that produces a realistic speech-like pattern
 * when the bot is talking. Produces random values in [0.2, 0.8] updated
 * every 80ms to drive smooth lip-sync animation on the Live2D avatar.
 */
@Composable
private fun simulatedAmplitude(botIsTalking: Boolean): Float {
    val amplitude by produceState(0f, botIsTalking) {
        if (botIsTalking) {
            while (true) {
                value = Random.nextFloat() * 0.6f + 0.2f // random between 0.2 and 0.8
                delay(80) // update every 80ms for smooth animation
            }
        } else {
            value = 0f
        }
    }
    return amplitude
}

/**
 * Main in-call layout showing the Live2D avatar and controls.
 *
 * Replaces the previous Daily SDK video renderer with a local Canvas-based
 * anime avatar (Live2DAvatarView) that uses audio-driven lip sync.
 */
@Composable
fun InCallLayout(
    botIsTalking: Boolean,
    userIsTalking: Boolean,
    micEnabled: Boolean,
    botReady: Boolean,
    errorMessage: String?,
    onToggleMic: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val amplitude = simulatedAmplitude(botIsTalking)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top bar with status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bot status
                SpeakingIndicator(
                    label = "数字人", // "数字人"
                    isSpeaking = botIsTalking
                )

                // Connection status
                Text(
                    text = if (botReady) "已连接" else "等待连接...", // "已连接" / "等待连接..."
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (botReady) Color.Green else Color.Gray
                )
            }

            // Error message
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(8.dp)
                )
            }

            // Avatar area -- replaces the Daily SDK VideoView
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF1A1A2E)), // dark background to make avatar pop
                contentAlignment = Alignment.Center
            ) {
                if (botReady) {
                    Live2DAvatarView(
                        isSpeaking = botIsTalking,
                        audioAmplitude = amplitude,
                        modifier = Modifier.size(300.dp)
                    )
                } else {
                    // Placeholder when not ready
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Show a dimmed avatar preview even before bot is ready
                        Live2DAvatarView(
                            isSpeaking = false,
                            audioAmplitude = 0f,
                            modifier = Modifier.size(200.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "等待数字人...", // "等待数字人..."
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Control buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic toggle
                FilledIconButton(
                    onClick = onToggleMic,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (micEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = if (micEnabled) "关闭麦克风" else "开启麦克风",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Disconnect button
                FilledIconButton(
                    onClick = onDisconnect,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "断开连接", // "断开连接"
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Bot talking indicator (visual feedback)
                FilledIconButton(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (botIsTalking) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = if (botIsTalking) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "数字人说话中", // "数字人说话中"
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
