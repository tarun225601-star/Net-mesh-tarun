package com.netmesh.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class NetMeshVpnService : VpnService(), SignalingClient.Listener {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var signalingClient: SignalingClient? = null
    private var webRtcManager: WebRtcManager? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())
        signalingClient = SignalingClient(getString(R.string.signaling_server_url), this)
        webRtcManager = WebRtcManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == VpnState.ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        val builder = Builder()
            .setSession("NetMesh")
            .addAddress("10.0.0.2", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)
            .setBlocking(true)

        vpnInterface = builder.establish()
        signalingClient?.connect()
        broadcastState(VpnState.CONNECTING)
    }

    private fun stopVpn() {
        vpnInterface?.close()
        vpnInterface = null
        signalingClient?.disconnect()
        webRtcManager?.cleanup()
        broadcastState(VpnState.DISCONNECTED)
        stopForeground(true)
        stopSelf()
    }

    private fun broadcastState(state: VpnState, error: String? = null) {
        val intent = Intent(VpnState.BROADCAST_ACTION).apply {
            putExtra(VpnState.EXTRA_STATE, state.name)
            if (error != null) putExtra(VpnState.EXTRA_ERROR, error)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createNotification(): Notification {
        val channelId = "vpn_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "VPN Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return Notification.Builder(this, channelId)
            .setContentTitle("NetMesh Active")
            .setSmallIcon(R.drawable.ic_vpn)
            .build()
    }

    // Signaling Listener Implementations
    override fun onConnected() {
        webRtcManager?.createOffer()
    }

    override fun onAnswer(sdp: String) {
        webRtcManager?.setRemoteDescription(sdp)
    }

    override fun onRemoteCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        webRtcManager?.addIceCandidate(sdpMid, sdpMLineIndex, candidate)
    }

    override fun onError(message: String) {
        broadcastState(VpnState.ERROR, message)
    }

    override fun onDisconnected() {
        broadcastState(VpnState.DISCONNECTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
