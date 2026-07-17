package com.netmesh.vpn

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.netmesh.vpn.databinding.ActivityMainBinding

private const val TAG = "NetMesh/MainActivity"

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentState = VpnState.DISCONNECTED

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "VPN permission is required", Toast.LENGTH_LONG).show()
            updateUI(VpnState.DISCONNECTED)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val stateName = intent.getStringExtra(VpnState.EXTRA_STATE) ?: return
            val error = intent.getStringExtra(VpnState.EXTRA_ERROR)
            updateUI(VpnState.valueOf(stateName), error)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupToggleButton()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            stateReceiver, IntentFilter(VpnState.BROADCAST_ACTION)
        )
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stateReceiver)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
            }
            webChromeClient = WebChromeClient()
            loadUrl("https://your-dashboard-url.com") // इसे अपनी URL से बदल लें
        }
    }

    private fun setupToggleButton() {
        binding.fabToggle.setOnClickListener {
            if (currentState == VpnState.CONNECTED || currentState == VpnState.CONNECTING) {
                stopVpnService()
            } else {
                handleConnectClick()
            }
        }
    }

    private fun handleConnectClick() {
        val intent = VpnService.prepare(this)
        if (intent == null) startVpnService()
        else vpnPermissionLauncher.launch(intent)
    }

    private fun startVpnService() {
        val intent = Intent(this, NetMeshVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateUI(VpnState.CONNECTING)
    }

    private fun stopVpnService() {
        startService(Intent(this, NetMeshVpnService::class.java).apply {
            action = VpnState.ACTION_STOP
        })
    }

    private fun updateUI(state: VpnState, error: String? = null) {
        currentState = state
        runOnUiThread {
            binding.tvStatus.text = if (state == VpnState.ERROR) error else state.name
            binding.fabToggle.isEnabled = true
        }
    }
}
