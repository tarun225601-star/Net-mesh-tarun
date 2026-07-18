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
        builder.addAddress("10.0.0.2", 24)
        builder.addDnsServer("8.8.8.8")
        builder.addRoute("0.0.0.0", 0)
        builder.addDisallowedApplication(packageName)
        
        vpnInterface = builder.establish()
        return START_STICKY
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }
}
