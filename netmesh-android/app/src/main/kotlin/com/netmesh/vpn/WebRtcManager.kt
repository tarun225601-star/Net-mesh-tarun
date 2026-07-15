package com.netmesh.vpn

import android.content.Context
import android.util.Log
import org.webrtc.*
import java.nio.ByteBuffer

private const val TAG = "NetMesh/WebRtc"

/**
 * Manages the WebRTC PeerConnection and binary DataChannel used as the VPN tunnel.
 *
 * Flow:
 *  1. [initialize] — creates PeerConnectionFactory
 *  2. [createOffer] — creates the PeerConnection + DataChannel, generates an SDP offer
 *  3. [setRemoteAnswer] — applies the SDP answer from the relay server
 *  4. [addRemoteCandidate] — feeds ICE candidates from the relay server
 *  5. Once the DataChannel is open:
 *       - [sendPacket] writes raw IP packets into the tunnel
 *       - [onPacketReceived] is called with packets arriving from the relay
 */
class WebRtcManager(
    private val context: Context,
    private val onPacketReceived: (ByteArray) -> Unit,
    private val onIceCandidate: (IceCandidate) -> Unit,
    private val onConnectionStateChange: (PeerConnection.IceConnectionState) -> Unit,
    private val onDataChannelOpen: () -> Unit,
    private val onDataChannelClose: () -> Unit
) {

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    // ── Initialization ────────────────────────────────────────────────────

    fun initialize() {
        val initOptions = PeerConnectionFactory.InitializationOptions
            .builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val options = PeerConnectionFactory.Options()
        factory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()

        Log.i(TAG, "PeerConnectionFactory initialized")
    }

    // ── Offer creation ────────────────────────────────────────────────────

    /**
     * Creates the PeerConnection and a binary DataChannel, then generates an SDP offer.
     * Calls [onOffer] with the SDP string when ready.
     */
    fun createOffer(iceServers: List<PeerConnection.IceServer>, onOffer: (String) -> Unit) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy   = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy  = PeerConnection.RtcpMuxPolicy.REQUIRE
            sdpSemantics   = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory!!.createPeerConnection(rtcConfig, object : PeerConnection.Observer {

            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "Local ICE candidate: ${candidate.sdp.take(60)}")
                this@WebRtcManager.onIceCandidate(candidate)
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.i(TAG, "ICE connection state: $state")
                onConnectionStateChange(state)
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                Log.i(TAG, "Peer connection state: $state")
            }

            override fun onDataChannel(dc: DataChannel) {
                // The relay may open a data channel to us — set up the observer
                Log.i(TAG, "Remote data channel opened: ${dc.label()}")
                setupDataChannelObserver(dc)
            }

            // ── Required overrides (no-op for VPN use-case) ──
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

        }) ?: run {
            Log.e(TAG, "Failed to create PeerConnection")
            return
        }

        // Create the data channel we'll use as the IP tunnel
        // ordered=false, maxRetransmits=0 → UDP-like, lowest latency
        // Change to ordered=true if you want reliable delivery (TCP-like apps need this anyway)
        val dcInit = DataChannel.Init().apply {
            ordered         = false
            maxRetransmits  = 0
            protocol        = "netmesh-vpn"
            negotiated      = false
        }
        dataChannel = peerConnection!!.createDataChannel("vpn-tunnel", dcInit)
        setupDataChannelObserver(dataChannel!!)

        // Generate the SDP offer
        val sdpConstraints = MediaConstraints()
        peerConnection!!.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.i(TAG, "SDP offer created")
                peerConnection!!.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.i(TAG, "Local description set")
                        onOffer(sdp.description)
                    }
                    override fun onSetFailure(error: String) {
                        Log.e(TAG, "setLocalDescription failed: $error")
                    }
                    override fun onCreateSuccess(sdp: SessionDescription) {}
                    override fun onCreateFailure(error: String) {}
                }, sdp)
            }
            override fun onCreateFailure(error: String) {
                Log.e(TAG, "createOffer failed: $error")
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }, sdpConstraints)
    }

    // ── Answer & ICE ─────────────────────────────────────────────────────

    fun setRemoteAnswer(sdp: String) {
        val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() { Log.i(TAG, "Remote answer set") }
            override fun onSetFailure(error: String) { Log.e(TAG, "setRemoteDescription failed: $error") }
            override fun onCreateSuccess(sdp: SessionDescription) {}
            override fun onCreateFailure(error: String) {}
        }, answer)
    }

    fun addRemoteCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        peerConnection?.addIceCandidate(iceCandidate)
    }

    // ── Packet I/O ────────────────────────────────────────────────────────

    /**
     * Send one raw IP packet (from the TUN interface) through the DataChannel.
     * Must only be called after the DataChannel is open.
     */
    fun sendPacket(packet: ByteArray) {
        val dc = dataChannel ?: return
        if (dc.state() != DataChannel.State.OPEN) return
        val buffer = DataChannel.Buffer(ByteBuffer.wrap(packet), true /* binary */)
        if (!dc.send(buffer)) {
            Log.w(TAG, "DataChannel.send returned false (buffer full?)")
        }
    }

    // ── DataChannel observer ──────────────────────────────────────────────

    private fun setupDataChannelObserver(dc: DataChannel) {
        dc.registerObserver(object : DataChannel.Observer {

            override fun onStateChange() {
                Log.i(TAG, "DataChannel state: ${dc.state()}")
                when (dc.state()) {
                    DataChannel.State.OPEN  -> onDataChannelOpen()
                    DataChannel.State.CLOSED,
                    DataChannel.State.CLOSING -> onDataChannelClose()
                    else -> {}
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                if (!buffer.binary) {
                    Log.w(TAG, "Received non-binary DataChannel message — ignoring")
                    return
                }
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                onPacketReceived(bytes)
            }

            override fun onBufferedAmountChange(previousAmount: Long) {}
        })
    }

    // ── Teardown ──────────────────────────────────────────────────────────

    fun dispose() {
        try {
            dataChannel?.close()
            dataChannel?.dispose()
            dataChannel = null
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null
            factory?.dispose()
            factory = null
            PeerConnectionFactory.stopInternalTracingCapture()
            PeerConnectionFactory.shutdownInternalTracer()
        } catch (e: Exception) {
            Log.e(TAG, "Error during WebRTC disposal", e)
        }
        Log.i(TAG, "WebRTC resources disposed")
    }
}
