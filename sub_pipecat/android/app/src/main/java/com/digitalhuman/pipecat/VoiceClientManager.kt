package com.digitalhuman.pipecat

import ai.pipecat.client.PipecatClient
import ai.pipecat.client.PipecatClientOptions
import ai.pipecat.client.PipecatEventCallbacks
import ai.pipecat.client.TransportState
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
    private var client: PipecatClient? = null

    // Mutable state properties for Compose UI
    val state: MutableState<TransportState?> = mutableStateOf(null)
    val botReady: MutableState<Boolean> = mutableStateOf(false)
    val botIsTalking: MutableState<Boolean> = mutableStateOf(false)
    val userIsTalking: MutableState<Boolean> = mutableStateOf(false)
    val mic: MutableState<Boolean> = mutableStateOf(true)
    val isConnecting: MutableState<Boolean> = mutableStateOf(false)
    val errorMessage: MutableState<String?> = mutableStateOf(null)
    val isDisconnected: MutableState<Boolean> = mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * RTVI event callbacks for Pipecat client events.
     * Handles transport state changes and bot/user speaking states.
     * No video tracks needed -- avatar is rendered locally via Live2DAvatarView.
     */
    private val callbacks = object : PipecatEventCallbacks() {
        override fun onTransportStateChanged(state: TransportState) {
            Log.d(TAG, "Transport state changed: $state")
            scope.launch {
                this@VoiceClientManager.state.value = state
                this@VoiceClientManager.isConnecting.value = state != TransportState.CONNECTED

                if (state == TransportState.DISCONNECTED) {
                    this@VoiceClientManager.isDisconnected.value = true
                }
            }
        }

        override fun onBotReady() {
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

        override fun onDisconnected(reason: String?) {
            Log.d(TAG, "Disconnected: $reason")
            scope.launch {
                this@VoiceClientManager.isDisconnected.value = true
                this@VoiceClientManager.state.value = TransportState.DISCONNECTED
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

        try {
            // Create SmallWebRTC transport
            // TODO: Verify exact import path -- may be:
            //   ai.pipecat.client.small_webrtc.SmallWebRTCTransport
            //   or ai.pipecat.small_webrtc.SmallWebRTCTransport
            // The SmallWebRTC transport handles WebRTC offer/answer via HTTP
            // to the Pipecat server's /connect endpoint.
            //
            // Expected factory pattern:
            //   val transport = SmallWebRTCTransport.Factory(context, baseUrl)
            //   or
            //   val transport = SmallWebRTCTransport(context)
            //
            // For now, using a placeholder that matches the likely API.
            // Adjust the constructor/factory call based on actual small-webrtc-transport API.
            val transportClass = Class.forName("ai.pipecat.client.small_webrtc.SmallWebRTCTransport")
            val transport = transportClass
                .getConstructor(Context::class.java, String::class.java)
                .newInstance(context, serverUrl)

            // Create client options with callbacks and base URL
            val options = PipecatClientOptions(
                callbacks = callbacks,
                baseUrl = serverUrl
            )

            // Create the Pipecat client with SmallWebRTC transport
            client = PipecatClient(transport, options)

            // Connect to the server
            // TODO: Verify exact API -- may need connect() or startBotAndConnect()
            client?.connect()?.let { future ->
                future.withCallback {
                    Log.d(TAG, "Connection callback completed")
                }
            } ?: run {
                Log.w(TAG, "Connect returned null future")
            }

        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "SmallWebRTC transport class not found. Ensure small-webrtc-transport " +
                "dependency is correctly declared.", e)
            scope.launch {
                errorMessage.value = "SmallWebRTC transport not available: ${e.message}"
                isConnecting.value = false
            }
        } catch (e: NoSuchMethodException) {
            Log.e(TAG, "SmallWebRTC transport constructor signature mismatch. " +
                "The API may differ from expected (Context, String).", e)
            scope.launch {
                errorMessage.value = "Transport API mismatch: ${e.message}"
                isConnecting.value = false
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
     * Controls whether user audio is being sent to the bot.
     */
    fun toggleMic() {
        val currentMicState = mic.value
        Log.d(TAG, "Toggling mic: $currentMicState -> ${!currentMicState}")

        try {
            client?.let { c ->
                // TODO: Verify exact API for mute/unmute
                // Possible APIs:
                //   c.setMicEnabled(!currentMicState)
                //   c.mute(!currentMicState)
                //   c.setUserAudioEnabled(!currentMicState)
                mic.value = !currentMicState
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling mic", e)
            errorMessage.value = "Failed to toggle microphone: ${e.message}"
        }
    }

    /**
     * Stop the client and disconnect from the server.
     * Cleans up all resources.
     */
    fun stop() {
        Log.d(TAG, "Stopping Pipecat client")

        try {
            client?.let { c ->
                try {
                    c.disconnect()
                } catch (e: Exception) {
                    // Try alternative API if disconnect fails
                    try {
                        c.stop()
                    } catch (e2: Exception) {
                        Log.w(TAG, "Error stopping client", e2)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping client", e)
        } finally {
            client = null
            scope.launch {
                state.value = TransportState.DISCONNECTED
                isDisconnected.value = true
                isConnecting.value = false
            }
        }
    }

    /**
     * Cleanup resources. Call this when the manager is no longer needed.
     */
    fun cleanup() {
        stop()
    }
}
