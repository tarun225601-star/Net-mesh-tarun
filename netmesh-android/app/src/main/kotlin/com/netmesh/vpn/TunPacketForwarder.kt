package com.netmesh.vpn

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class TunPacketForwarder(
    private val tunInterface: ParcelFileDescriptor,
    private val dataChannel: org.webrtc.DataChannel
) : Runnable {

    private val inputStream = FileInputStream(tunInterface.fileDescriptor)
    private val outputStream = FileOutputStream(tunInterface.fileDescriptor)
    private var isRunning = true

    override fun run() {
        val buffer = ByteBuffer.allocate(32767)
        try {
            while (isRunning) {
                val length = inputStream.read(buffer.array())
                if (length > 0) {
                    val data = ByteBuffer.allocate(length)
                    data.put(buffer.array(), 0, length)
                    data.flip()
                    val payload = org.webrtc.DataChannel.Buffer(data, false)
                    dataChannel.send(payload)
                }
            }
        } catch (e: Exception) {
            Log.e("TunForwarder", "Error forwarding packets", e)
        }
    }

    fun write(data: ByteBuffer) {
        try {
            outputStream.write(data.array(), 0, data.remaining())
        } catch (e: Exception) {
            Log.e("TunForwarder", "Error writing to TUN", e)
        }
    }

    fun stop() {
        isRunning = false
    }
}
