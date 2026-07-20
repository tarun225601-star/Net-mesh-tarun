package com.netmesh.vpn

import android.content.Context
import android.util.Log
import org.webrtc.*
import java.nio.ByteBuffer

class WebRtcManager(private val context: Context) {

    private val TAG = "NetMesh/Final"
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    
    // NetMeshVpnService.kt का एरर ठीक करने के लिए यह लिसनर जोड़ा है
    private var dataChannelOpenListener: (() -> Unit)? = null

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        startConnection()
    }

    // सर्विस फाइल के लिए यह फंक्शन बनाना जरूरी था
    fun setOnDataChannelOpenListener(listener: () -> Unit) {
        this.dataChannelOpenListener = listener
    }

    private fun startConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        ))

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                Log.d(TAG, "ICE Candidate found: ${candidate?.sdp}")
            }

            override fun onDataChannel(dc: DataChannel?) {
                dataChannel = dc
                setupDataChannel()
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE Connection State: $state")
            }

            // यह फंक्शन न होने से कंपाइलर एरर दे रहा था
            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d(TAG, "ICE Connection Receiving: $receiving")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            
            override fun onAddStream(stream: MediaStream?) {}
            
            override fun onRemoveStream(stream: MediaStream?) {}
            
            override fun onRenegotiationNeeded() {}
            
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })

        val init = DataChannel.Init()
        dataChannel = peerConnection?.createDataChannel("vizia-tunnel", init)
        setupDataChannel()

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(this, sdp)
                Log.d(TAG, "Offer ready!")
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {}
            override fun onSetFailure(err: String?) {}
        }, MediaConstraints())
    }

    private fun setupDataChannel() {
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onMessage(buffer: DataChannel.Buffer) {
                // डेटा पैकेट हैंडल करने के लिए
            }

            override fun onStateChange() {
                Log.d(TAG, "DataChannel State: ${dataChannel?.state()}")
                if (dataChannel?.state() == DataChannel.State.OPEN) {
                    dataChannelOpenListener?.invoke() // लिसनर को ट्रिगर करें
                }
            }

            // यह फंक्शन भी मिसिंग था, इसे जोड़ दिया है
            override fun onBufferedAmountChange(previousAmount: Long) {
                Log.d(TAG, "Buffered amount changed: $previousAmount")
            }
        })
    }

    fun sendPacket(packet: ByteArray) {
        if (dataChannel?.state() == DataChannel.State.OPEN) {
            dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap(packet), false))
        }
    }
}
