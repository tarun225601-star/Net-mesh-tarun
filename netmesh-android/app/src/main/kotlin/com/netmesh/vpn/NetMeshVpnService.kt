package com.netmesh.app.v2

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

class NetMeshVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        isRunning = true
        vpnThread = Thread({
            try {
                val builder = Builder().apply {
                    setSession("NetMeshTunnel")
                    addAddress("10.0.0.2", 24)
                    addRoute("0.0.0.0", 0)
                    addDnsServer("8.8.8.8")
                    addDnsServer("8.8.4.4")
                }

                vpnInterface = builder.establish()

                vpnInterface?.let { pfd ->
                    val inputStream = FileInputStream(pfd.fileDescriptor)
                    val outputStream = FileOutputStream(pfd.fileDescriptor)
                    val packet = ByteBuffer.allocate(32767)

                    while (isRunning) {
                        val length = inputStream.read(packet.array())
                        if (length > 0) {
                            packet.limit(length)
                            outputStream.write(packet.array(), 0, length)
                            outputStream.flush()
                            packet.clear()
                        } else {
                            Thread.sleep(10)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, "NetMeshTunnelThread")

        vpnThread?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        vpnThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        vpnInterface = null
    }
}
