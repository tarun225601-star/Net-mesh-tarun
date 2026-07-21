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
                    setSession("NetMeshSplitTunnel")
                    // वर्चुअल आईपी एड्रेस
                    addAddress("10.0.0.2", 24)
                    // बाकी सारा ट्रैफिक टनल में भेजने के लिए रूट
                    addRoute("0.0.0.0", 0)

                    // SPLIT TUNNELING: क्रोम या ब्राउज़र जिसमें वेबसाइट चल रही है, 
                    // उसे वीपीएन से बाहर (Disallow) रखते हैं ताकि वह डायरेक्ट मोबाइल नेट से चले और कभी क्रैश/स्लीप न हो।
                    try {
                        addDisallowedApplication("com.android.chrome")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                vpnInterface = builder.establish()

                vpnInterface?.let { pfd ->
                    val inputStream = FileInputStream(pfd.fileDescriptor)
                    val buffer = ByteBuffer.allocate(32767)

                    while (isRunning) {
                        try {
                            val length = inputStream.read(buffer.array())
                            if (length > 0) {
                                buffer.clear()
                            } else {
                                Thread.sleep(10)
                            }
                        } catch (e: IOException) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cleanup()
            }
        }, "NetMeshVpnThread")
        vpnThread?.start()
    }

    private fun cleanup() {
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
        }
        vpnInterface = null
        isRunning = false
    }

    override fun onDestroy() {
        isRunning = false
        vpnThread?.interrupt()
        cleanup()
        super.onDestroy()
    }
}
