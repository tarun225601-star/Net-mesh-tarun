package com.netmesh.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

class NetMeshVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var webRtcManager: WebRtcManager? = null // सुनिश्चित करें कि यह इनिशियलाइज़्ड है
    private var tunPacketForwarder: TunPacketForwarder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = Builder()
        builder.setSession("NetMeshTunnel")
        builder.setMtu(1500)
        builder.addAddress("10.8.0.2", 24)
        builder.addDnsServer("8.8.8.8")
        builder.addRoute("0.0.0.0", 0)

        try {
            vpnInterface = builder.establish()
            setupWebRtcListener()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return START_STICKY
    }

    private fun setupWebRtcListener() {
        // webRtcManager को सही जगह पर इनिशियलाइज़ करें
        webRtcManager?.setOnDataChannelOpenListener {
            vpnInterface?.let { fd ->
                tunPacketForwarder = TunPacketForwarder(fd, webRtcManager!!)
                tunPacketForwarder?.startForwarding()
                Log.d("NetMeshVpnService", "Data channel opened, forwarding started")
            }
        }
    }

    override fun onDestroy() {
        tunPacketForwarder?.stopForwarding()
        vpnInterface?.close()
        super.onDestroy()
    }
}
