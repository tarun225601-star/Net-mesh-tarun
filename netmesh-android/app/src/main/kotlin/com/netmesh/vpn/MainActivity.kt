package com.netmesh.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // एक बड़ा बटन बनाना
        val btn = Button(this)
        btn.text = "Connect VPN"
        btn.textSize = 24f
        btn.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        btn.setOnClickListener {
            if (!isConnected) {
                // VPN परमिशन चेक करना
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    startActivityForResult(intent, 0)
                } else {
                    startService(Intent(this, NetMeshVpnService::class.java))
                }
                btn.text = "Disconnect"
                isConnected = true
            } else {
                // VPN बंद करना
                stopService(Intent(this, NetMeshVpnService::class.java))
                btn.text = "Connect VPN"
                isConnected = false
            }
        }
        
        setContentView(btn)
    }
}
