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
        val builder = Builder()
        builder.setSession("NetMeshTunnel")

        builder.setMtu(1500)
        builder.addAddress("10.8.0.2", 24)
        builder.addDnsServer("8.8.8.8")
        builder.addSearchDomain("com")
        builder.addRoute("0.0.0.0", 0)

        try {
            vpnInterface = builder.establish()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // यहाँ से डेटा चैनल ओपन होने पर फॉरवर्डिंग शुरू की जाएगी
        setupWebRtcListener()
        
        return START_STICKY
    }

    private fun setupWebRtcListener() {
        // यह मानकर कि webRtcManager में एक callback listener है
        webRtcManager?.setOnDataChannelOpenListener {
            vpnInterface?.let { fd ->
                tunPacketForwarder = TunPacketForwarder(fd, webRtcManager!!)
                tunPacketForwarder?.startForwarding()
                Log.d("NetMeshVpnService", "Data channel opened, forwarding started.")
            }
        }
    }

    override fun onDestroy() {
        // सर्विस बंद होने पर फॉरवर्डिंग को साफ़ करना ज़रूरी है
        tunPacketForwarder?.stopForwarding() 
        vpnInterface?.close()
        super.onDestroy()
    }
}
