package com.netmesh.vpn

import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

class TunPacketForwarder(
    private val tunFd: ParcelFileDescriptor,
    private val webRtcManager: WebRtcManager // आपका WebRTC मैनेजर क्लास
) {
    @Volatile var isRunning = true
    val upBytes = AtomicLong(0)
    val downBytes = AtomicLong(0)

    fun startForwarding() {
        val inputStream = FileInputStream(tunFd.fileDescriptor)
        val outputStream = FileOutputStream(tunFd.fileDescriptor)
        val buffer = ByteArray(1500) // MTU 1500 के साथ मैच

        // TUN -> WebRTC Loop
        Thread {
            try {
                while (isRunning) {
                    val length = inputStream.read(buffer)
                    if (length == -1) break // EOF मिलते ही लूप तोड़ें
                    if (length > 0) {
                        upBytes.addAndGet(length.toLong())
                        webRtcManager.sendPacket(buffer.copyOf(length))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // WebRTC -> TUN path
    fun onPacketFromRelay(packet: ByteArray) {
        synchronized(this) {
            val outputStream = FileOutputStream(tunFd.fileDescriptor)
            outputStream.write(packet)
            downBytes.addAndGet(packet.size.toLong())
        }
    }
}
