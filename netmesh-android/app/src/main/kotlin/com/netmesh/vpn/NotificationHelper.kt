package com.netmesh.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Creates and updates the persistent foreground service notification.
 *
 * A foreground service MUST call startForeground(id, notification) within 5 seconds
 * of starting, otherwise the OS kills it. This helper provides that notification.
 */
object NotificationHelper {

    const val NOTIFICATION_ID = 1001
    private const val CHANNEL_ID = "netmesh_vpn"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN Tunnel",
            NotificationManager.IMPORTANCE_LOW   // LOW = no sound, but shows in status bar
        ).apply {
            description    = "Shows while the NetMesh VPN tunnel is active"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    /**
     * Build the persistent foreground notification.
     *
     * Tapping the notification opens MainActivity.
     * The "Disconnect" action sends ACTION_STOP to the service.
     */
    fun buildNotification(context: Context, status: String = "Routing all traffic"): Notification {
        // Tap → open MainActivity
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Disconnect" action → stop the service
        val stopIntent = PendingIntent.getService(
            context, 1,
            Intent(context, NetMeshVpnService::class.java).apply {
                action = VpnState.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("NetMesh VPN Active")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_lock_lock)  // replace with your own ic_vpn_key
            .setContentIntent(openIntent)
            .setOngoing(true)           // can't be swiped away while service is running
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_delete, "Disconnect", stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun update(context: Context, status: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(context, status))
    }
}
