package com.netmesh.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

class NetMeshVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var webRtcManager: WebRtcManager? = null
    private var tunPacketForwarder: TunPacketForwarder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_VPN") {
            stopSelf()
            return START_NOT_STICKY
        }

        // यहाँ इनिशियलाइज़ेशन फिक्स किया है
        webRtcManager = WebRtcManager(this)

        val builder = Builder()
        builder.setSession("NetMeshTunnel")
        builder.setMtu(1500)
        builder.addAddress("10.8.0.2", 24)
        builder.addDnsServer("8.8.8.8")
        builder.addRoute("223.237.25.168", 32)

        try {
            vpnInterface = builder.establish()
            setupWebRtcListener()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return START_STICKY
    }

    private fun setupWebRtcListener() {
        // अब webRtcManager null नहीं होगा
        webRtcManager?.setOnDataChannelOpenListener {
            vpnInterface?.let { fd ->
                tunPacketForwarder = TunPacketForwarder(fd, webRtcManager!!)
                tunPacketForwarder?.startForwarding()
                Log.d("NetMeshVpnService", "Data channel open and forwarding started")
            }
        }
    }

    override fun onDestroy() {
        tunPacketForwarder?.stopForwarding()
        vpnInterface?.close()
        super.onDestroy()
    }
}
