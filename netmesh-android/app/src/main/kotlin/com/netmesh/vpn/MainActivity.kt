package com.example.netmesh // अपना सही पैकेज नाम डालें

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

        val btnToggle = findViewById<Button>(R.id.btnVpnToggle)

        btnToggle.setOnClickListener {
            if (isVpnRunning) {
                stopService(Intent(this, NetMeshVpnService::class.java))
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
    
    // अनुमति मिलने के बाद सर्विस शुरू करने के लिए
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            startService(Intent(this, NetMeshVpnService::class.java))
            findViewById<Button>(R.id.btnVpnToggle).text = "Disconnect"
            isVpnRunning = true
        }
    }
}
