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
        
        // वाइट स्क्रीन फिक्स और वेबव्यू सेटिंग्स
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true 
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.webViewClient = WebViewClient()
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
