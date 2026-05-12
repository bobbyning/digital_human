package com.digitalhuman.pipecat

import ai.pipecat.client.PipecatClient
import ai.pipecat.client.PipecatClientOptions
import ai.pipecat.client.PipecatEventCallbacks
import ai.pipecat.client.small_webrtc_transport.IceConfig
import ai.pipecat.client.small_webrtc_transport.SmallWebRTCTransport
import ai.pipecat.client.small_webrtc_transport.SmallWebRTCTransportConnectParams
import ai.pipecat.client.types.APIRequest
import ai.pipecat.client.types.BotReadyData
import ai.pipecat.client.types.TransportState
import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manages the Pipecat client lifecycle and state.
 * Uses SmallWebRTC transport for real-time voice communication.
 *
 * SmallWebRTC is an open-source, zero-API-key alternative to Daily transport.
 * It handles WebRTC offer/answer exchange via HTTP to the Pipecat server.
 */
class VoiceClientManager(private val context: Context) {
    companion object {
        private const val TAG = "VoiceClientManager"
    }

    // Client state
    private var client: PipecatClient<SmallWebRTCTransport, SmallWebRTCTransportConnectParams>? = null

    // Mutable state properties for Compose UI
    val state: MutableState<TransportState?> = mutableStateOf(null)
    val botReady: MutableState<Boolean> = mutableStateOf(false)
    val botIsTalking: MutableState<Boolean> = mutableStateOf(false)
    val userIsTalking: MutableState<Boolean> = mutableStateOf(false)
    val mic: MutableState<Boolean> = mutableStateOf(true)
    val isConnecting: MutableState<Boolean> = mutableStateOf(false)
    val errorMessage: MutableState<String?> = mutableStateOf(null)
    val isDisconnected: MutableState<Boolean> = mutableStateOf(false)

    // Remote video track from the bot (streamed via SmallWebRTC from the
    // server-side MuseTalk renderer). Updated whenever a new track arrives.
    val videoTrack: MutableState<Any?> = mutableStateOf(null)

    // EglBase context needed to initialize SurfaceViewRenderer for video playback.
    val eglBaseContext: MutableState<Any?> = mutableStateOf(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * RTVI event callbacks for Pipecat client events.
     */
    private val callbacks = object : PipecatEventCallbacks() {
        override fun onTransportStateChanged(state: TransportState) {
            Log.d(TAG, "Transport state changed: $state")
            scope.launch {
                this@VoiceClientManager.state.value = state
                this@VoiceClientManager.isConnecting.value = state != TransportState.Connected

                if (state == TransportState.Disconnected) {
                    this@VoiceClientManager.isDisconnected.value = true
                }
            }
        }

        override fun onBotReady(data: BotReadyData) {
            Log.d(TAG, "Bot is ready")
            scope.launch {
                this@VoiceClientManager.botReady.value = true
            }
        }

        override fun onBotStartedSpeaking() {
            Log.d(TAG, "Bot started speaking")
            scope.launch {
                this@VoiceClientManager.botIsTalking.value = true
            }
        }

        override fun onBotStoppedSpeaking() {
            Log.d(TAG, "Bot stopped speaking")
            scope.launch {
                this@VoiceClientManager.botIsTalking.value = false
            }
        }

        override fun onUserStartedSpeaking() {
            Log.d(TAG, "User started speaking")
            scope.launch {
                this@VoiceClientManager.userIsTalking.value = true
            }
        }

        override fun onUserStoppedSpeaking() {
            Log.d(TAG, "User stopped speaking")
            scope.launch {
                this@VoiceClientManager.userIsTalking.value = false
            }
        }

        override fun onDisconnected() {
            Log.d(TAG, "Disconnected")
            scope.launch {
                this@VoiceClientManager.isDisconnected.value = true
                this@VoiceClientManager.state.value = TransportState.Disconnected
            }
        }

        override fun onBackendError(message: String) {
            Log.e(TAG, "Backend error: $message")
            scope.launch {
                this@VoiceClientManager.errorMessage.value = message
            }
        }
    }

    /**
     * Start the Pipecat client and connect to the server.
     * @param serverUrl The base URL of the Pipecat server (e.g., "http://192.168.1.100:7860")
     */
    fun start(serverUrl: String) {
        Log.d(TAG, "Starting Pipecat client with SmallWebRTC transport, URL: $serverUrl")

        // Reset state
        isConnecting.value = true
        errorMessage.value = null
        isDisconnected.value = false
        botReady.value = false
        videoTrack.value = null

        try {
            // Create SmallWebRTC transport with default ICE config
            val iceConfig = IceConfig(emptyList())
            val transport = SmallWebRTCTransport(context, iceConfig)

            // Create client options with callbacks
            val options = PipecatClientOptions(
                callbacks = callbacks,
                enableMic = true,
                enableCam = false
            )

            // Create the Pipecat client with SmallWebRTC transport
            client = PipecatClient(transport, options)

            // Connect to the server
            val connectParams = SmallWebRTCTransportConnectParams(
                webrtcRequestParams = APIRequest(
                    endpoint = "",
                    requestData = ai.pipecat.client.types.Value.Str("")
                ),
                iceConfig = iceConfig
            )

            client?.let { c ->
                scope.launch {
                    try {
                        val result = c.connect(connectParams)
                        Log.d(TAG, "Connect result: $result")
                    } catch (e: Exception) {
                        Log.e(TAG, "Connect failed", e)
                        errorMessage.value = "Connection failed: ${e.message}"
                        isConnecting.value = false
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting client", e)
            scope.launch {
                errorMessage.value = "Failed to start: ${e.message}"
                isConnecting.value = false
            }
        }
    }

    /**
     * Toggle the microphone on/off.
     */
    fun toggleMic() {
        val currentMicState = mic.value
        Log.d(TAG, "Toggling mic: $currentMicState -> ${!currentMicState}")

        try {
            client?.let { c ->
                scope.launch {
                    try {
                        c.enableMic(!currentMicState)
                        mic.value = !currentMicState
                    } catch (e: Exception) {
                        Log.e(TAG, "Error toggling mic", e)
                        errorMessage.value = "Failed to toggle microphone: ${e.message}"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling mic", e)
            errorMessage.value = "Failed to toggle microphone: ${e.message}"
        }
    }

    /**
     * Stop the client and disconnect from the server.
     */
    fun stop() {
        Log.d(TAG, "Stopping Pipecat client")

        try {
            client?.let { c ->
                scope.launch {
                    try {
                        c.disconnect()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error disconnecting client", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping client", e)
        } finally {
            client = null
            scope.launch {
                state.value = TransportState.Disconnected
                isDisconnected.value = true
                isConnecting.value = false
                videoTrack.value = null
            }
        }
    }

    /**
     * Cleanup resources.
     */
    fun cleanup() {
        stop()
    }
}
