package com.netmesh.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private var isVpnRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnToggle = findViewById<Button>(R.id.vpnButton)

        btnToggle.setOnClickListener {
            if (isVpnRunning) {
                val stopIntent = Intent(this, NetMeshVpnService::class.java)
                stopIntent.action = "STOP_VPN"
                startService(stopIntent)
                btnToggle.text = "Connect"
                isVpnRunning = false
            } else {
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    startActivityForResult(intent, 0)
                } else {
                    startService(Intent(this, NetMeshVpnService::class.java))
                    btnToggle.text = "Disconnect"
                    isVpnRunning = true
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            startService(Intent(this, NetMeshVpnService::class.java))
            findViewById<Button>(R.id.vpnButton).text = "Disconnect"
            isVpnRunning = true
        }
    }
}
