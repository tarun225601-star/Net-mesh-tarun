package com.netmesh.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        // तुम्हारा सही लिंक यहाँ है
        webView.loadUrl("https://netmesh-fix-live9.onrender.com")

        val vpnButton = findViewById<Button>(R.id.vpnButton)
        vpnButton.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 0)
            } else {
                val vpnIntent = Intent(this, NetMeshVpnService::class.java)
                vpnIntent.action = "TOGGLE_VPN"
                startService(vpnIntent)
            }
        }
    }
}
