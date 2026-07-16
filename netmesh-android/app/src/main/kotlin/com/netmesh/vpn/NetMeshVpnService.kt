package com.netmesh.vpn

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection

private const val TAG = "NetMesh/VpnService"

/**
 * NetMeshVpnService — the core of the native VPN implementation.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ What this class does                                                    │
 * │                                                                         │
 * │  1. Starts as a Foreground Service (required for long-running VPNs).   │
 * │  2. Calls VpnService.Builder to create a virtual TUN network            │
 * │     interface at 10.0.0.2/24 that captures all device IP traffic.      │
 * │  3. Establishes a WebRTC PeerConnection + DataChannel to your relay.   │
 * │  4. Runs two loops:                                                     │
 * │       • TUN → DataChannel: reads raw IP packets from the TUN fd,       │
 * │         forwards them to the relay over WebRTC.                        │
 * │       • DataChannel → TUN: receives packets from the relay,            │
 * │         writes them back to the TUN fd (returned responses).           │
 * │  5. Broadcasts VpnState changes to MainActivity for UI updates.        │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * Lifecycle:
 *   startForegroundService(Intent) → onStartCommand → build TUN → WebRTC handshake
 *   Intent(ACTION_STOP) or stopSelf() → onDestroy → teardown everything
 */
class NetMeshVpnService : VpnService() {

    // ── State ─────────────────────────────────────────────────────────────

    private var tunInterface: ParcelFileDescriptor? = null
    private var webRtcManager: WebRtcManager? = null
    private var signalingClient: SignalingClient? = null
    private var tunForwarder: TunPacketForwarder? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Foreground Service entry point ────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle "Disconnect" action from the notification or MainActivity
        if (intent?.action == VpnState.ACTION_STOP) {
            Log.i(TAG, "Stop action received")
            teardown()
            stopSelf()
            return START_NOT_STICKY
        }

        // Create the persistent notification and promote to foreground ASAP
        // (must happen within 5 seconds or the OS terminates us)
        NotificationHelper.createChannel(this)
        startForeground(NotificationHelper.NOTIFICATION_ID, NotificationHelper.buildNotification(this, "Connecting…"))

        Log.i(TAG, "NetMeshVpnService starting")
        broadcastState(VpnState.CONNECTING)

        scope.launch { startVpnTunnel() }

        // START_STICKY: if the process is killed, Android recreates the service with a null intent
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        teardown()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── VPN tunnel setup ──────────────────────────────────────────────────

    private suspend fun startVpnTunnel() {
        // ── Step 1: Check VPN permission ──────────────────────────────────
        // VpnService.prepare() returns a non-null Intent if the permission
        // has not yet been granted. In that case we cannot proceed —
        // the permission dialog can only be shown from an Activity context,
        // not from a Service. Tell the UI to re-show the dialog.
        val permissionIntent = prepare(this)
        if (permissionIntent != null) {
            Log.e(TAG, "VPN permission not yet granted — stopping service")
            broadcastState(VpnState.ERROR, "VPN permission required — open the app and tap Connect")
            stopSelf()
            return
        }

        // ── Step 2: Build the TUN interface ───────────────────────────────
        tunInterface = try {
            buildTunInterface()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build TUN interface", e)
            broadcastState(VpnState.ERROR, "TUN interface failed: ${e.message}")
            stopSelf()
            return
        }
        Log.i(TAG, "TUN interface created")

        // ── Step 3: Initialize WebRTC ─────────────────────────────────────
        val signalingUrl = getString(R.string.signaling_server_url)
        setupWebRtc(signalingUrl)
    }

    // ── TUN interface builder ─────────────────────────────────────────────

    /**
     * Creates the virtual network interface.
     *
     * Key settings:
     *  - Address 10.0.0.2/24 — the device's VPN IP
     *  - Route 0.0.0.0/0    — capture ALL IPv4 traffic (change to specific
     *                          subnets if you want split tunneling)
     *  - Route ::/0          — capture ALL IPv6 traffic (optional)
     *  - MTU 1500            — standard Ethernet MTU; keep ≤ your relay's MTU
     *  - DNS 1.1.1.1         — Cloudflare DNS routed through the tunnel
     *
     * The returned ParcelFileDescriptor is the "TUN fd": reading it gives
     * outbound IP packets; writing to it injects inbound IP packets.
     */
    private fun buildTunInterface(): ParcelFileDescriptor {
        val builder = Builder()
            .setSession("NetMesh")
            .setMtu(1500)
            // Device's virtual IP inside the tunnel
            .addAddress("10.0.0.2", 24)
            // Route all IPv4 traffic through the tunnel
            .addRoute("0.0.0.0", 0)
            // Route all IPv6 traffic through the tunnel (comment out for IPv4-only)
            .addRoute("::", 0)
            // DNS servers — resolved via the tunnel
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            // Do NOT add our own signaling server's IP to the route table
            // (it would create a routing loop). Add it here to bypass the VPN.
            // .addDisallowedApplication("com.yourapp") // to exclude apps from VPN

        // Allow the app itself to reach the signaling server through the real network
        // without going through the VPN (prevents a routing loop)
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "addDisallowedApplication failed — proceeding without exclusion", e)
        }

        return builder.establish()
            ?: throw IllegalStateException("VpnService.Builder.establish() returned null — check permission")
    }

    // ── WebRTC setup ──────────────────────────────────────────────────────

    private fun setupWebRtc(signalingUrl: String) {
        // Pending ICE candidates from the relay that arrived before the
        // remote description was set — we queue them and drain after setRemoteAnswer
        val pendingRemoteCandidates = mutableListOf<Triple<String, Int, String>>()
        var remoteDescriptionSet = false

        val manager = WebRtcManager(
            context = this,
            onPacketReceived = { packet ->
                // A raw IP packet arrived from the relay — write it to the TUN fd
                tunForwarder?.onPacketFromRelay(packet)
            },
            onIceCandidate = { candidate: IceCandidate ->
                // Local ICE candidate ready — send it to the relay via signaling
                signalingClient?.sendCandidate(
                    candidate.sdpMid,
                    candidate.sdpMLineIndex,
                    candidate.sdp
                )
            },
            onConnectionStateChange = { state: PeerConnection.IceConnectionState ->
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        // ICE connected — DataChannel open callback fires separately
                        Log.i(TAG, "ICE connected")
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        Log.e(TAG, "ICE connection failed")
                        broadcastState(VpnState.ERROR, "ICE connection failed")
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        Log.w(TAG, "ICE disconnected — will attempt reconnect")
                        broadcastState(VpnState.CONNECTING)
                    }
                    else -> {}
                }
            },
            onDataChannelOpen = {
                Log.i(TAG, "DataChannel OPEN — VPN tunnel active")
                // Start bridging TUN ↔ DataChannel
                val tun = tunInterface ?: return@WebRtcManager
                tunForwarder = TunPacketForwarder(tun, webRtcManager!!) { down, up ->
                    broadcastStats(down, up)
                }
                tunForwarder?.start()
                NotificationHelper.update(this, "Routing all traffic through WebRTC tunnel")
                broadcastState(VpnState.CONNECTED)
            },
            onDataChannelClose = {
                Log.w(TAG, "DataChannel CLOSED")
                tunForwarder?.stop()
                broadcastState(VpnState.ERROR, "Tunnel closed — try reconnecting")
            }
        )

        manager.initialize()
        webRtcManager = manager

        // ICE servers — use STUN for basic connectivity.
        // Add TURN servers here for reliability behind symmetric NAT:
        //   PeerConnection.IceServer.builder("turn:your-turn-server:3478")
        //       .setUsername("user").setPassword("pass").createIceServer()
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        // Connect to the signaling server and start the WebRTC handshake
        signalingClient = SignalingClient(signalingUrl, object : SignalingClient.Listener {

            override fun onConnected() {
                Log.i(TAG, "Signaling connected — creating WebRTC offer")
                manager.createOffer(iceServers) { offerSdp ->
                    signalingClient?.sendOffer(offerSdp)
                }
            }

            override fun onAnswer(sdp: String) {
                Log.i(TAG, "SDP answer received from relay")
                manager.setRemoteAnswer(sdp)
                remoteDescriptionSet = true
                // Drain any ICE candidates that arrived before the answer
                synchronized(pendingRemoteCandidates) {
                    pendingRemoteCandidates.forEach { (mid, index, cand) ->
                        manager.addRemoteCandidate(mid, index, cand)
                    }
                    pendingRemoteCandidates.clear()
                }
            }

            override fun onRemoteCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
                if (remoteDescriptionSet) {
                    manager.addRemoteCandidate(sdpMid, sdpMLineIndex, candidate)
                } else {
                    // Queue candidates until the remote description is set
                    synchronized(pendingRemoteCandidates) {
                        pendingRemoteCandidates.add(Triple(sdpMid, sdpMLineIndex, candidate))
                    }
                }
            }

            override fun onError(message: String) {
                Log.e(TAG, "Signaling error: $message")
                broadcastState(VpnState.ERROR, message)
            }

            override fun onDisconnected() {
                Log.w(TAG, "Signaling disconnected")
                // The WebRTC connection may still be alive after signaling drops —
                // ICE keep-alives keep it open. Only move to error if DataChannel closes.
            }
        })

        signalingClient?.connect()
    }

    // ── Teardown ──────────────────────────────────────────────────────────

    /**
     * Cleanly shuts down all resources in order:
     *  1. Stop packet forwarder (stops coroutine loops, closes TUN streams)
     *  2. Dispose WebRTC (closes DataChannel and PeerConnection)
     *  3. Disconnect signaling WebSocket
     *  4. Close TUN file descriptor (removes the virtual network interface)
     *  5. Cancel coroutine scope
     */
    private fun teardown() {
        Log.i(TAG, "Tearing down VPN tunnel")

        tunForwarder?.stop()
        tunForwarder = null

        webRtcManager?.dispose()
        webRtcManager = null

        signalingClient?.disconnect()
        signalingClient = null

        try {
            tunInterface?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing TUN fd", e)
        }
        tunInterface = null

        scope.cancel()

        broadcastState(VpnState.DISCONNECTED)
        Log.i(TAG, "VPN tunnel torn down")
    }

    // ── Broadcast helpers ─────────────────────────────────────────────────

    private fun broadcastState(state: VpnState, error: String? = null) {
        val intent = Intent(VpnState.BROADCAST_ACTION).apply {
            putExtra(VpnState.EXTRA_STATE, state.name)
            error?.let { putExtra(VpnState.EXTRA_ERROR, it) }
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastStats(down: Long, up: Long) {
        val intent = Intent(VpnState.BROADCAST_STATS).apply {
            putExtra(VpnState.EXTRA_BYTES_DOWN, down)
            putExtra(VpnState.EXTRA_BYTES_UP, up)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}
