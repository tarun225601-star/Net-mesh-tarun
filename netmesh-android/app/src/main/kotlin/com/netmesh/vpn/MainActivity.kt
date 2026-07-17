package com.netmesh.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // VPN परमिशन के लिए लॉन्चर
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. WebView सेटअप
        val webView = findViewById<WebView>(R.id.webView)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        webView.webViewClient = WebViewClient()
        webView.loadUrl("https://netmesh-fix-live9.onrender.com")

        // 2. VPN बटन सेटअप
        val vpnButton = Button(this)
        vpnButton.text = "VPN"
        val params = FrameLayout.LayoutParams(200, 150, Gravity.END or Gravity.BOTTOM)
        params.setMargins(0, 0, 50, 50)
        addContentView(vpnButton, params)

        // 3. बटन क्लिक पर VPN परमिशन मांगना
        vpnButton.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                vpnPermissionLauncher.launch(intent)
            } else {
                startVpnService()
            }
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, NetMeshVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
