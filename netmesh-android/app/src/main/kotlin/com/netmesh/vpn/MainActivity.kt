package com.netmesh.vpn

import android.os.Bundle
import android.view.Gravity
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. WebView सेटअप - यहाँ आपकी वेबसाइट लोड होगी
        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        webView.loadUrl("https://netmesh-fix-live9.onrender.com")

        // 2. फ्लोटिंग VPN बटन सेटअप
        val vpnButton = Button(this)
        vpnButton.text = "VPN"
        
        // बटन को स्क्रीन के निचले दाएं कोने (Bottom-Right) पर सेट करना
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.BOTTOM
        )
        params.setMargins(0, 0, 50, 50) // कोने से थोड़ी दूरी (Margin)
        
        addContentView(vpnButton, params)
        
        vpnButton.setOnClickListener {
            // यहाँ आपके VPN कनेक्ट करने का लॉजिक काम करेगा
        }
    }
}
