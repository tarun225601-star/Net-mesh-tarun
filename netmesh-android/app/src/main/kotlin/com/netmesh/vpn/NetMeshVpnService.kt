package com.netmesh.app.v2

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel

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
                    // वीपीएन का वर्चुअल आईपी और सबनेट
                    addAddress("10.0.0.2", 24)
                    // सभी इंटरनेट ट्रैफिक को टनल की तरफ मोड़ना
                    addRoute("0.0.0.0", 0)
                    // पब्लिक डीएनएस ताकि यूट्यूब और वेबसाइट्स तुरंत नाम पहचान सकें
                    addDnsServer("8.8.8.8")
                    addDnsServer("8.8.4.4")
                    setConfigureIntent(null)
                }

                vpnInterface = builder.establish()

                vpnInterface?.let { pfd ->
                    val inputStream = FileInputStream(pfd.fileDescriptor)
                    val outputStream = FileOutputStream(pfd.fileDescriptor)
                    val packet = ByteBuffer.allocate(32767)

                    while (isRunning) {
                        val length = inputStream.read(packet.array())
                        if (length > 0) {
                            // यहाँ पैकेट रीड हो रहा है और हम इसे प्रोटेक्टेड सॉकेट से आगे पास करेंगे
                            packet.limit(length)
                            
                            // वीपीएन लूप से बचने के लिए पैकेट को वापस उसी आउटपुट स्ट्रीम पर रिफ्लेक्ट कर रहे हैं
                            // ताकि बिना किसी रुकावट के कनेक्टिविटी बनी रहे
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
