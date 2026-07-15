package com.netmesh.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives BOOT_COMPLETED and optionally restarts the VPN service.
 *
 * To enable auto-reconnect on boot:
 *  1. Store the user's "connect on boot" preference in SharedPreferences.
 *  2. Uncomment the auto-start block below.
 *
 * Note: The VPN permission dialog CANNOT be shown from a BroadcastReceiver.
 * If the user has already granted VPN permission in this session, startService()
 * works. If not, the service will start but VpnService.prepare() in
 * NetMeshVpnService will return a non-null Intent and the service must stop itself.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i("NetMesh/Boot", "Device booted")

        // ── Optional: auto-reconnect on boot ──────────────────────────────
        // val prefs = context.getSharedPreferences("netmesh", Context.MODE_PRIVATE)
        // val autoConnect = prefs.getBoolean("auto_connect_on_boot", false)
        // if (autoConnect) {
        //     val serviceIntent = Intent(context, NetMeshVpnService::class.java)
        //     context.startForegroundService(serviceIntent)
        // }
    }
}
