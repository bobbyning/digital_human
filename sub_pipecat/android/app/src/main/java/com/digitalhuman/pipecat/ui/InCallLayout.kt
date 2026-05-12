package com.digitalhuman.pipecat.ui

import android.view.ViewGroup
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import co.daily.client.Daily
import co.daily.client.ParticipantId
import co.daily.client.VideoView

/**
 * Video renderer for displaying the bot's video track.
 * Uses Daily SDK's VideoView for efficient video rendering.
 *
 * TODO: Verify integration with Pipecat tracks.
 * The Daily SDK VideoView typically expects a participant ID,
 * but RTVI/Pipecat may provide raw video tracks that need different handling.
 * Adjust the video rendering approach based on actual Pipecat SDK API.
 */
@Composable
fun BotVideoView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Create Daily SDK VideoView for rendering
    val videoView = remember {
        VideoView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // TODO: Set up video track rendering
            // Depending on Pipecat SDK API, you may need to:
            // 1. Use setParticipantId() if using Daily room mode
            // 2. Use setVideoTrack() directly if using raw tracks
            // 3. Use Daily.createCallObject() and manage tracks manually
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            videoView.onDestroy()
        }
    }

    AndroidView(
        factory = { videoView },
        modifier = modifier
    )
}

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
 * Main in-call layout showing the video feed and controls.
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
                    label = "数字人",
                    isSpeaking = botIsTalking
                )

                // Connection status
                Text(
                    text = if (botReady) "已连接" else "等待连接...",
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

            // Video area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (botReady) {
                    // Render bot video
                    BotVideoView(
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Placeholder when not ready
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(120.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "等待数字人视频...",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Gray
                        )
                    }
                }
            }

            // User speaking indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                SpeakingIndicator(
                    label = "我",
                    isSpeaking = userIsTalking
                )
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
                        contentDescription = "断开连接",
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
                        contentDescription = "数字人说话中",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
