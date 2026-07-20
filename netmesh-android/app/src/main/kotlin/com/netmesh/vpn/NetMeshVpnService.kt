package com.netmesh.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class NetMeshVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = Builder()
        
        // VPN कॉन्फ़िगरेशन
        vpnInterface = builder.setSession("NetMeshVPN")
            .addAddress("10.0.0.2", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)
            .establish()
            
        return START_STICKY
    }

    override fun onDestroy() {
        vpnInterface?.close()
        super.onDestroy()
    }
}
