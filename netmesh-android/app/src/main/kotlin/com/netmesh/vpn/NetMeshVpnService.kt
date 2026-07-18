package com.netmesh.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class NetMeshVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_VPN") {
            onDestroy()
            return START_NOT_STICKY
        }
        
        val builder = Builder()
        builder.setSession("NetMeshTunnel")
        // MTU फिक्स और नेटवर्क स्टेबिलिटी बदलाव
        builder.setMtu(1400) 
        builder.addAddress("192.168.0.2", 24)
        builder.addDnsServer("1.1.1.1")
        builder.addRoute("0.0.0.0", 0)
        builder.addDisallowedApplication(packageName)
        
        vpnInterface = builder.establish()
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vpnInterface = null
        super.onDestroy()
    }
}
