class MainActivity : AppCompatActivity() {
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val btn = Button(this)
        btn.text = "Connect VPN"
        btn.textSize = 24f
        btn.layoutParams = ViewGroup.LayoutParams(500, 500) // बड़ा बटन

        btn.setOnClickListener {
            if (!isConnected) {
                // VPN स्टार्ट करने का परमिशन मांगें
                val intent = VpnService.prepare(this)
                if (intent != null) startActivityForResult(intent, 0)
                else startService(Intent(this, NetMeshVpnService::class.java))
                btn.text = "Disconnect"
                isConnected = true
            } else {
                stopService(Intent(this, NetMeshVpnService::class.java))
                btn.text = "Connect VPN"
                isConnected = false
            }
        }
        setContentView(btn)
    }
}
