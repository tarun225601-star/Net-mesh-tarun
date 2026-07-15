package com.netmesh.vpn

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.netmesh.vpn.databinding.ActivityMainBinding

private const val TAG = "NetMesh/MainActivity"

/**
 * MainActivity — the single screen of the NetMesh VPN app.
 *
 * Responsibilities:
 *  • Show current VPN state (Disconnected / Connecting / Connected / Error)
 *  • Handle the Android VPN permission dialog (VpnService.prepare())
 *  • Start / stop NetMeshVpnService
 *  • Display live traffic statistics (bytes up/down)
 *  • Request POST_NOTIFICATIONS permission on Android 13+
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var currentState = VpnState.DISCONNECTED

    // ── Permission launchers ──────────────────────────────────────────────

    /**
     * Launched when VpnService.prepare() returns a non-null Intent.
     * The system shows the "NetMesh wants to set up a VPN connection" dialog.
     * RESULT_OK → start the VPN service.
     */
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.i(TAG, "VPN permission granted — starting service")
            startVpnService()
        } else {
            Log.w(TAG, "VPN permission denied by user")
            Toast.makeText(this, "VPN permission is required to connect", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Android 13+ notification permission.
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Log.w(TAG, "Notification permission denied — foreground notification won't show")
        }
    }

    // ── BroadcastReceiver — listens for state changes from the service ─────

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                VpnState.BROADCAST_ACTION -> {
                    val stateName = intent.getStringExtra(VpnState.EXTRA_STATE) ?: return
                    val error     = intent.getStringExtra(VpnState.EXTRA_ERROR)
                    val state     = VpnState.valueOf(stateName)
                    applyState(state, error)
                }
                VpnState.BROADCAST_STATS -> {
                    val down = intent.getLongExtra(VpnState.EXTRA_BYTES_DOWN, 0L)
                    val up   = intent.getLongExtra(VpnState.EXTRA_BYTES_UP,   0L)
                    updateStats(down, up)
                }
            }
        }
    }

    // ── Activity lifecycle ────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Update the server URL display from resources
        binding.tvServerInfo.text = "Signal: ${getString(R.string.signaling_server_url)}"

        binding.btnConnect.setOnClickListener {
            when (currentState) {
                VpnState.DISCONNECTED, VpnState.ERROR -> handleConnectClick()
                VpnState.CONNECTED, VpnState.CONNECTING -> handleDisconnectClick()
            }
        }

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(VpnState.BROADCAST_ACTION)
            addAction(VpnState.BROADCAST_STATS)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(stateReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stateReceiver)
    }

    // ── Connect / Disconnect logic ────────────────────────────────────────

    private fun handleConnectClick() {
        // VpnService.prepare() either:
        //  - Returns null → permission already granted, we can start immediately
        //  - Returns an Intent → we must launch it so the user sees the system dialog
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent == null) {
            Log.i(TAG, "VPN permission already granted")
            startVpnService()
        } else {
            Log.i(TAG, "Requesting VPN permission via system dialog")
            vpnPermissionLauncher.launch(prepareIntent)
        }
    }

    private fun handleDisconnectClick() {
        Log.i(TAG, "User requested disconnect")
        stopVpnService()
    }

    private fun startVpnService() {
        val intent = Intent(this, NetMeshVpnService::class.java)
        startForegroundService(intent)
        applyState(VpnState.CONNECTING)
    }

    private fun stopVpnService() {
        val intent = Intent(this, NetMeshVpnService::class.java).apply {
            action = VpnState.ACTION_STOP
        }
        startService(intent)   // sends ACTION_STOP to the running service
        applyState(VpnState.DISCONNECTED)
    }

    // ── UI updates ────────────────────────────────────────────────────────

    private fun applyState(state: VpnState, error: String? = null) {
        currentState = state
        runOnUiThread {
            when (state) {
                VpnState.DISCONNECTED -> {
                    binding.tvStatus.text = getString(R.string.status_disconnected)
                    binding.viewStatusDot.backgroundTintList =
                        getColorStateList(R.color.red_disconnected)
                    binding.btnConnect.text = getString(R.string.connect)
                    binding.btnConnect.backgroundTintList = getColorStateList(R.color.purple_500)
                    binding.btnConnect.isEnabled = true
                    binding.layoutStats.visibility = View.GONE
                }
                VpnState.CONNECTING -> {
                    binding.tvStatus.text = getString(R.string.status_connecting)
                    binding.viewStatusDot.backgroundTintList =
                        getColorStateList(R.color.orange_connecting)
                    binding.btnConnect.text = "Connecting…"
                    binding.btnConnect.backgroundTintList = getColorStateList(R.color.orange_connecting)
                    binding.btnConnect.isEnabled = true   // allow cancel
                    binding.layoutStats.visibility = View.GONE
                }
                VpnState.CONNECTED -> {
                    binding.tvStatus.text = getString(R.string.status_connected)
                    binding.viewStatusDot.backgroundTintList =
                        getColorStateList(R.color.green_connected)
                    binding.btnConnect.text = getString(R.string.disconnect)
                    binding.btnConnect.backgroundTintList = getColorStateList(R.color.red_disconnected)
                    binding.btnConnect.isEnabled = true
                    binding.layoutStats.visibility = View.VISIBLE
                }
                VpnState.ERROR -> {
                    val msg = error ?: getString(R.string.status_error)
                    binding.tvStatus.text = msg
                    binding.viewStatusDot.backgroundTintList =
                        getColorStateList(R.color.red_disconnected)
                    binding.btnConnect.text = getString(R.string.connect)
                    binding.btnConnect.backgroundTintList = getColorStateList(R.color.purple_500)
                    binding.btnConnect.isEnabled = true
                    binding.layoutStats.visibility = View.GONE
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateStats(down: Long, up: Long) {
        runOnUiThread {
            binding.tvBytesDown.text = formatBytes(down)
            binding.tvBytesUp.text   = formatBytes(up)
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1_024L                    -> "$bytes B"
            bytes < 1_048_576L               -> "%.1f KB".format(bytes / 1_024.0)
            bytes < 1_073_741_824L           -> "%.1f MB".format(bytes / 1_048_576.0)
            else                              -> "%.2f GB".format(bytes / 1_073_741_824.0)
        }
    }
}
