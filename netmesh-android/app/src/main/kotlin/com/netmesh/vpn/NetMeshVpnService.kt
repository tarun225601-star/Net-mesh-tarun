override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
 package com.netmesh.vpn
package com.netmesh.vpn

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
   if (intent?.action == "TOGGLE_VPN") {
        // अगर सर्विस पहले से चल रही है तो बंद करें
        stopForeground(true)
        stopSelf()
        return START_NOT_STICKY
    }
    
    val notification = NotificationHelper.buildNotification(this, "Active")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    } else {
        startForeground(NotificationHelper.NOTIFICATION_ID, notification)
    }
    return START_STICKY
}
