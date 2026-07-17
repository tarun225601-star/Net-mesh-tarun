package com.netmesh.vpn

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build

class NetMeshVpnService : VpnService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = Builder()
        builder.setSession("NetMesh")
        builder.addAddress("10.0.0.2", 24)
        builder.addRoute("0.0.0.0", 0)
        
        // चाबी (Notification) दिखाने के लिए
        val channelId = "vpn_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_vpn_config)
            .setContentTitle("NetMesh Active")
            .build()
        startForeground(1, notification)

        try {
            builder.establish()
        } catch (e: Exception) {}
        
        return START_STICKY
    }
}
