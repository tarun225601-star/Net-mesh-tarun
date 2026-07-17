package com.netmesh.vpn

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "NetMesh/Signaling"

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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val isConnecting = AtomicBoolean(false)
    private val isDisconnected = AtomicBoolean(false)
    private val retryCount = AtomicInteger(0)

    fun connect() {
        if (isConnecting.getAndSet(true)) return
        doConnect()
    }

    private fun doConnect() {
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                retryCount.set(0)
                isConnecting.set(false)
                listener.onConnected()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    when (json.getString("type")) {
                        "answer" -> listener.onAnswer(json.getString("sdp"))
                        "candidate" -> listener.onRemoteCandidate(
                            json.optString("sdpMid", "0"),
                            json.optInt("sdpMLineIndex", 0),
                            json.getString("candidate")
                        )
                        "error" -> listener.onError(json.optString("message", "Unknown error"))
                    }
                } catch (e: Exception) { Log.e(TAG, "Parse error", e) }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                isConnecting.set(false)
                if (!isDisconnected.get() && retryCount.get() < 3) {
                    retryCount.incrementAndGet()
                    doConnect()
                } else {
                    listener.onDisconnected()
                }
            }
        })
    }

    fun sendOffer(sdp: String) {
        val msg = JSONObject().apply {
            put("type", "offer")
            put("sdp", sdp)
        }.toString()
        webSocket?.send(msg)
    }

    fun sendCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val msg = JSONObject().apply {
            put("type", "candidate")
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
            put("candidate", candidate)
        }.toString()
        webSocket?.send(msg)
    }

    fun disconnect() {
        isDisconnected.set(true)
        webSocket?.close(1000, "Disconnecting")
    }
}
