package com.digitalhuman.pipecat

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.digitalhuman.pipecat.ui.ConnectScreen
import com.digitalhuman.pipecat.ui.InCallLayout
import com.digitalhuman.pipecat.ui.theme.DigitalHumanPipecatTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

/**
 * Sealed class representing the app's navigation state.
 */
sealed class AppState {
    data object Connecting : AppState()
    data class InCall(val serverUrl: String) : AppState()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DigitalHumanPipecatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainContent()
                }
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    private fun MainContent() {
        val recordAudioPermission = rememberPermissionState(
            permission = Manifest.permission.RECORD_AUDIO
        )

        // App state
        var appState by remember { mutableStateOf<AppState?>(null) }

        // Voice client manager
        val voiceClientManager = remember {
            VoiceClientManager(this@MainActivity)
        }

        when {
            // Step 1: Check audio permission
            !recordAudioPermission.status.isGranted -> {
                PermissionRequestScreen(
                    onRequestPermission = {
                        recordAudioPermission.launchPermissionRequest()
                    }
                )
            }

            // Step 2: Show connect screen (not yet connected)
            appState == null -> {
                ConnectScreen(
                    onConnect = { serverUrl ->
                        appState = AppState.InCall(serverUrl)
                        lifecycleScope.launch {
                            voiceClientManager.start(serverUrl)
                        }
                    },
                    isConnecting = voiceClientManager.isConnecting.value,
                    errorMessage = voiceClientManager.errorMessage.value
                )
            }

            // Step 3: In-call screen
            appState is AppState.InCall -> {
                InCallLayout(
                    botIsTalking = voiceClientManager.botIsTalking.value,
                    userIsTalking = voiceClientManager.userIsTalking.value,
                    micEnabled = voiceClientManager.mic.value,
                    botReady = voiceClientManager.botReady.value,
                    errorMessage = voiceClientManager.errorMessage.value,
                    onToggleMic = {
                        voiceClientManager.toggleMic()
                    },
                    onDisconnect = {
                        voiceClientManager.stop()
                        appState = null
                    },
                    videoTrack = voiceClientManager.videoTrack.value,
                    eglBaseContext = voiceClientManager.eglBaseContext.value
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Note: voiceClientManager cleanup should happen here
        // but since it's created in Compose, we rely on the remember block
        // and the stop() call from disconnect button
    }
}

/**
 * Permission request screen shown when RECORD_AUDIO permission is not granted.
 */
@Composable
private fun PermissionRequestScreen(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "需要麦克风权限",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "此应用需要麦克风权限才能进行语音通话",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("授予权限")
        }
    }
}
