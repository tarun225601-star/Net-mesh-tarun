package com.netmesh.vpn

/**
 * Shared state enum broadcast via LocalBroadcastManager.
 * MainActivity observes this to update the UI.
 * NetMeshVpnService sends updates as it moves through its lifecycle.
 */
enum class VpnState {
    DISCONNECTED,
    CONNECTING,     // permission granted, building TUN + WebRTC
    CONNECTED,      // DataChannel open, packets flowing
    ERROR;

    companion object {
        /** Intent action sent via LocalBroadcastManager */
        const val BROADCAST_ACTION = "com.netmesh.vpn.VPN_STATE_CHANGED"
        /** Extra key carrying the VpnState name string */
        const val EXTRA_STATE = "state"
        /** Extra key carrying an optional human-readable error message */
        const val EXTRA_ERROR = "error"
        /** Intent action to command the service to stop */
        const val ACTION_STOP = "com.netmesh.vpn.ACTION_STOP"
        /** Extra keys for traffic stats broadcasts */
        const val EXTRA_BYTES_DOWN = "bytes_down"
        const val EXTRA_BYTES_UP   = "bytes_up"
        /** Stats broadcast action — sent every second while connected */
        const val BROADCAST_STATS  = "com.netmesh.vpn.VPN_STATS"
    }
}
