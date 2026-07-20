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

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        startConnection()
    }

    private fun startConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        ))

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                // यहाँ से कैंडिडेट JSON फॉर्मेट में सर्वर को भेजें
                Log.d(TAG, "सिग्नलिंग: सर्वर को यह कैंडिडेट भेजें: ${candidate?.sdp}")
            }

            override fun onDataChannel(dc: DataChannel?) {
                dataChannel = dc
                setupDataChannel()
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE स्थिति: $state")
            }
            // अन्य जरूरी खाली मेथड्स...
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })

        // 1. डेटा चैनल बनाएं
        val init = DataChannel.Init()
        dataChannel = peerConnection?.createDataChannel("vizia-tunnel", init)
        setupDataChannel()

        // 2. ऑफर बनाएं (हैंडशेक की शुरुआत)
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(this, sdp)
                Log.d(TAG, "ऑफर तैयार! इसे अपने Render सर्वर के API पर भेजें।")
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) { Log.e(TAG, "ऑफर फेल: $err") }
            override fun onSetFailure(err: String?) {}
        }, MediaConstraints())
    }

    private fun setupDataChannel() {
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onMessage(buffer: DataChannel.Buffer) {
                // सर्वर से डेटा आ रहा है
            }
            override fun onStateChange() {
                if (dataChannel?.state() == DataChannel.State.OPEN) {
                    Log.d(TAG, "✅ टनल पूरी तरह खुल गई है! अब नेट चलना चाहिए।")
                }
            }
        })
    }

    fun sendPacket(packet: ByteArray) {
        if (dataChannel?.state() == DataChannel.State.OPEN) {
            dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap(packet), false))
        }
    }
}
