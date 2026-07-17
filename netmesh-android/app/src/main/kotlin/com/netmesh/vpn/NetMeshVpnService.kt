package com.netmesh.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build

class NetMeshVpnService : VpnService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. नोटिफिकेशन चैनल बनाएं (Android 8+ के लिए जरूरी)
        val channelId = "vpn_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, 
                "VPN Service", 
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        // 2. नोटिफिकेशन बनाएं
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("NetMesh VPN")
                .setContentText("VPN connection is active")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("NetMesh VPN")
                .setContentText("VPN connection is active")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build()
        }

        // 3. सर्विस को फॉरग्राउंड में डालें ताकि ऐप बंद न हो
        startForeground(1, notification)

        return START_STICKY
    }
}
