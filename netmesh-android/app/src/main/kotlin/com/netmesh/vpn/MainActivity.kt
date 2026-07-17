package com.netmesh.vpn

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // WebView सेटअप
        val webView = findViewById<WebView>(R.id.webView)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // चित्रों और अन्य कंटेंट के लिए
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        
        webView.webViewClient = WebViewClient()
        webView.loadUrl("https://netmesh-fix-live9.onrender.com")

        // VPN बटन सेटअप
        val vpnButton = Button(this)
        vpnButton.text = "VPN"
        
        val params = FrameLayout.LayoutParams(200, 150, Gravity.END or Gravity.BOTTOM)
        params.setMargins(0, 0, 50, 50)
        addContentView(vpnButton, params)

        // बटन क्लिक पर VPN शुरू करना
        vpnButton.setOnClickListener {
            val intent = Intent(this, NetMeshVpnService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }
}
