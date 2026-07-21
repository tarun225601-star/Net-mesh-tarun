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
                    // बाकी सारा ट्रैफिक टनल में भेजने के लिए राउट
                    addRoute("0.0.0.0", 0)
                    
                    // यूट्यूब और इंटरनेट के डीएनएस रिजॉल्यूशन के लिए पब्लिक डीएनएस जोड़ा गया है
                    addDnsServer("8.8.8.8")

                    // SPLIT TUNNELING: क्रोम या ब्राउज़र को वीपीएन से बाहर (Disallow) रखना
                    try {
                        addDisallowedApplication("com.android.chrome")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                vpnInterface = builder.establish()

                vpnInterface?.let { pfd ->
                    val inputStream = FileInputStream(pfd.fileDescriptor)
                    val outputStream = FileOutputStream(pfd.fileDescriptor)
                    val buffer = ByteBuffer.allocate(32767)

                    while (isRunning) {
                        try {
                            val length = inputStream.read(buffer.array())
                            if (length > 0) {
                                buffer.clear()
                            }
                            Thread.sleep(10)
                        } catch (e: IOException) {
                            e.printStackTrace()
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, "NetMeshVpnThread")

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
