package com.netmesh.vpn

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "NetMesh/Signaling"

/**
 * WebSocket-based signaling client.
 *
 * Exchanges SDP offers/answers and ICE candidates with your relay server.
 *
 * Protocol (all messages are JSON):
 *   → { "type": "offer",     "sdp": "<sdp string>" }
 *   ← { "type": "answer",    "sdp": "<sdp string>" }
 *   ↔ { "type": "candidate", "sdpMid": "0", "sdpMLineIndex": 0, "candidate": "<...>" }
 *   ← { "type": "error",     "message": "<reason>" }
 */
class SignalingClient(
    private val serverUrl: String,
    private val listener: Listener
) {

    interface Listener {
        fun onConnected()
        fun onAnswer(sdp: String)
        fun onRemoteCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String)
        fun onError(message: String)
        fun onDisconnected()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // keep-alive — no read timeout
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    fun connect() {
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected to $serverUrl")
                listener.onConnected()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d(TAG, "← $text")
                try {
                    val json = JSONObject(text)
                    when (json.getString("type")) {
                        "answer" -> {
                            val sdp = json.getString("sdp")
                            listener.onAnswer(sdp)
                        }
                        "candidate" -> {
                            val mid   = json.optString("sdpMid", "0")
                            val index = json.optInt("sdpMLineIndex", 0)
                            val cand  = json.getString("candidate")
                            listener.onRemoteCandidate(mid, index, cand)
                        }
                        "error" -> {
                            listener.onError(json.optString("message", "Unknown signaling error"))
                        }
                        else -> Log.w(TAG, "Unknown message type: ${json.getString("type")}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse signaling message", e)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                listener.onError("WebSocket error: ${t.message}")
                listener.onDisconnected()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code / $reason")
                listener.onDisconnected()
            }
        })
    }

    /** Send our SDP offer to the relay server */
    fun sendOffer(sdp: String) {
        val msg = JSONObject().apply {
            put("type", "offer")
            put("sdp", sdp)
        }.toString()
        Log.d(TAG, "→ offer")
        webSocket?.send(msg)
    }

    /** Send a local ICE candidate to the relay server */
    fun sendCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val msg = JSONObject().apply {
            put("type", "candidate")
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
            put("candidate", candidate)
        }.toString()
        Log.d(TAG, "→ candidate")
        webSocket?.send(msg)
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        client.dispatcher.executorService.shutdown()
    }
}
