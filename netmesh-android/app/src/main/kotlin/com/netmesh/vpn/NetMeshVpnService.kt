package com.netmesh.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class NetMeshVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = Builder()
        builder.setSession("NetMeshTunnel")
        
        // Android 14+ और स्थिरता के लिए MTU 1500
        builder.setMtu(1500) 
        builder.addAddress("10.8.0.2", 24)
        builder.addDnsServer("8.8.8.8")
        builder.addSearchDomain("com")
        builder.addRoute("0.0.0.0", 0)
        
        // डायनामिक ऐप एक्सक्लूजन ताकि VPN लूप न बने
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        vpnInterface = builder.establish()
        return START_STICKY
    }

    override fun onDestroy() {
        vpnInterface?.close()
        super.onDestroy()
    }
}
