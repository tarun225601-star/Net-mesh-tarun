package com.netmesh.vpn

import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

class TunPacketForwarder(
    private val tunFd: ParcelFileDescriptor,
    private val webRtcManager: WebRtcManager
) {
    @Volatile
    private var isRunning = false
    private val upBytes = AtomicLong(0)
    private val downBytes = AtomicLong(0)

    fun stopForwarding() {
        isRunning = false
    }

    fun startForwarding() {
        if (isRunning) return
        isRunning = true

        Thread {
            try {
                // FileInputStream और FileOutputStream का उपयोग सही तरीके से करें
                val inputStream = FileInputStream(tunFd.fileDescriptor)
                val outputStream = FileOutputStream(tunFd.fileDescriptor)
                val buffer = ByteArray(1350)

                while (isRunning) {
                    val length = inputStream.read(buffer)
                    if (length == -1) break
                    if (length > 0) {
                        upBytes.addAndGet(length.toLong())
                        val packet = buffer.copyOf(length)
                        webRtcManager.sendPacket(packet) // यह फंक्शन WebRtcManager में होना चाहिए
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun onPacketFromRelay(packet: ByteArray) {
        synchronized(this) {
            val outputStream = FileOutputStream(tunFd.fileDescriptor)
            outputStream.write(packet)
            downBytes.addAndGet(packet.size.toLong())
        }
    }
}
