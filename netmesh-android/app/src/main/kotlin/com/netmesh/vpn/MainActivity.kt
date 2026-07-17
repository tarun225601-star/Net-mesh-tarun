override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val webView = findViewById<WebView>(R.id.webView)
    webView.settings.javaScriptEnabled = true
    webView.webViewClient = WebViewClient()
    webView.loadUrl("https://netmesh-fix-live9.onrender.com")

    // अब बटन को XML से ढूँढें
    val vpnButton = findViewById<Button>(R.id.vpnButton)
    vpnButton.setOnClickListener {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }
}
