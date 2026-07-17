package com.netmesh.vpn // अपनी सही पैकेजिंग चेक कर लें

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // सुनिश्चित करें कि XML का नाम सही है

        val myWebView: WebView = findViewById(R.id.webView)
        
        // वेबव्यू सेटिंग्स को ठीक से इनिशियलाइज़ करना
        myWebView.settings.javaScriptEnabled = true
        myWebView.webViewClient = WebViewClient()
        myWebView.webChromeClient = WebChromeClient()
        
        // अपना URL लोड करें
        myWebView.loadUrl("https://google.com") 
    }
}
