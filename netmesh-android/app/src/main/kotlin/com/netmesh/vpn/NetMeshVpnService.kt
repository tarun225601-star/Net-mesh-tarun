package com.netmesh.vpn

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder

class NetMeshVpnService : VpnService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // अगर टॉगल का एक्शन है तो सर्विस बंद करें
        if (intent?.action == "TOGGLE_VPN") {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        
        // अन्यथा सर्विस चालू रखें
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
