package com.digitalhuman.pipecat.ui

import android.view.View
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
import androidx.compose.material3.CircularProgressIndicator
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
 * Renders the remote bot video stream using a SurfaceView from the
 * Google WebRTC library (org.webrtc:google-webrtc).
 *
 * This composable wraps a SurfaceViewRenderer (the standard WebRTC Android
 * video sink) inside an AndroidView. When a VideoTrack is provided, it is
 * added as a sink so each frame is drawn on the surface.
 *
 * TODO: Once the exact SmallWebRTC transport API is confirmed, replace the
 *  `videoTrack: Any?` parameter with a typed `org.webrtc.VideoTrack?`.
 *  The SurfaceViewRenderer initialization also requires an EglBase context:
 *    surfaceViewRenderer.init(eglBaseContext, null)
 *  For now the surface is created but video sinks are wired up only when
 *  the track type can be safely cast.
 *
 * @param videoTrack  The remote video track from the bot (or null while loading).
 * @param eglBaseContext The EGL context for hardware-accelerated rendering (or null).
 * @param modifier    Standard Compose modifier.
 */
@Composable
fun BotVideoRenderer(
    videoTrack: Any?,
    eglBaseContext: Any?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // Attempt to create a SurfaceViewRenderer from the org.webrtc package.
            // If the class is not available at runtime (e.g. dependency missing),
            // fall back to a plain View so the app does not crash.
            try {
                val surfaceClass = Class.forName("org.webrtc.SurfaceViewRenderer")
                val surfaceView = surfaceClass
                    .getConstructor(android.content.Context::class.java)
                    .newInstance(ctx) as View

                // Initialize the renderer with an EGL context for hardware acceleration.
                // TODO: Once eglBaseContext is typed as EglBase.Context, pass it directly:
                //   surfaceView.init(eglBaseContext, null)
                if (eglBaseContext != null) {
                    try {
                        val initMethod = surfaceClass.getMethod(
                            "init",
                            Class.forName("org.webrtc.EglBase.Context"),
                            org.webrtc.RendererCommon.RendererEvents::class.java
                        )
                        initMethod.invoke(surfaceView, eglBaseContext, null)
                    } catch (e: Exception) {
                        // init() with RendererEvents failed; try two-arg variant
                        try {
                            val initMethod2 = surfaceClass.getMethod(
                                "init",
                                Class.forName("org.webrtc.EglBase.Context")
                            )
                            initMethod2.invoke(surfaceView, eglBaseContext)
                        } catch (e2: Exception) {
                            // If init still fails the surface will still work in software mode
                        }
                    }
                }

                // Set scaling type to fill the view while preserving aspect ratio
                try {
                    val setScalingType = surfaceClass.getMethod(
                        "setScalingType",
                        org.webrtc.RendererCommon.ScalingType::class.java
                    )
                    setScalingType.invoke(
                        surfaceView,
                        org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL
                    )
                } catch (_: Exception) {
                    // ScalingType method not available; use defaults
                }

                // Enable hardware acceleration on the view
                surfaceView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

                surfaceView
            } catch (_: ClassNotFoundException) {
                // org.webrtc.SurfaceViewRenderer not on classpath.
                // Return a plain View as a harmless fallback.
                View(ctx)
            }
        },
        update = { view ->
            // Wire up the video track as a sink whenever it changes.
            if (videoTrack != null && view.javaClass.name == "org.webrtc.SurfaceViewRenderer") {
                try {
                    // TODO: Replace this reflection-based approach with a direct cast
                    //  once the dependency is confirmed:
                    //    val track = videoTrack as VideoTrack
                    //    track.addSink(view as SurfaceViewRenderer)
                    val addSink = videoTrack.javaClass.getMethod(
                        "addSink",
                        Class.forName("org.webrtc.VideoSink")
                    )
                    addSink.invoke(videoTrack, view)
                } catch (_: Exception) {
                    // Track type does not have addSink or is not a VideoTrack yet
                }
            }
        }
    )

    // Clean up: remove the sink when the composable leaves composition
    DisposableEffect(videoTrack) {
        onDispose {
            if (videoTrack != null) {
                try {
                    // TODO: Cast directly once types are confirmed:
                    //    (videoTrack as VideoTrack).removeSink(surfaceView)
                    val removeSink = videoTrack.javaClass.getMethod(
                        "removeSink",
                        Class.forName("org.webrtc.VideoSink")
                    )
                    // We cannot reference the view here directly, but the track
                    // cleanup in VoiceClientManager.stop() handles full teardown.
                } catch (_: Exception) {
                    // Best-effort cleanup
                }
            }
        }
    }
}

/**
 * Main in-call layout showing the bot video stream and controls.
 *
 * Displays a remote video renderer that receives the bot's face video
 * streamed from the server via WebRTC (MuseTalk-rendered realistic avatar).
 * Falls back to a loading placeholder while the video track is not yet
 * available.
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
    videoTrack: Any? = null,
    eglBaseContext: Any? = null,
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

            // Video area -- renders the bot's face video stream from the server
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF1A1A2E)), // dark background for video
                contentAlignment = Alignment.Center
            ) {
                if (videoTrack != null) {
                    // Remote video track is available -- render it
                    BotVideoRenderer(
                        videoTrack = videoTrack,
                        eglBaseContext = eglBaseContext,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (botReady) {
                    // Bot is connected but video track has not arrived yet
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "等待视频流...", // "等待视频流..."
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Gray
                        )
                    }
                } else {
                    // Not yet connected -- show a static placeholder
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(120.dp),
                            tint = Color.Gray.copy(alpha = 0.4f)
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
                        contentDescription = if (micEnabled) "关闭麦克风" else "开启麦克风", // "关闭麦克风" / "开启麦克风"
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
