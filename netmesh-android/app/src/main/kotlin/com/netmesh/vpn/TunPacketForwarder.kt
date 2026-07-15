package com.netmesh.vpn

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "NetMesh/TunForwarder"

/**
 * Bridges the TUN file descriptor and the WebRTC DataChannel with two coroutine loops:
 *
 *  Loop A — TUN → WebRTC:
 *    Reads raw IP packets from the TUN fd (each read = one complete IP packet).
 *    Passes each packet to [webRtcManager] which sends it over the DataChannel.
 *
 *  Loop B — WebRTC → TUN:
 *    WebRtcManager calls [onPacketFromRelay] for each DataChannel message.
 *    Each message is a raw IP packet; we write it directly to the TUN fd.
 *
 * IP packet MTU: We read up to 65535 bytes per call (theoretical IP max).
 * In practice packets are ≤ 1500 bytes on Ethernet-based paths.
 */
class TunPacketForwarder(
    private val tunFd: ParcelFileDescriptor,
    private val webRtcManager: WebRtcManager,
    private val onBytesChanged: (down: Long, up: Long) -> Unit
) {

    // Traffic statistics — atomics so UI thread can read safely
    private val bytesDown = AtomicLong(0L)   // relay → TUN (download)
    private val bytesUp   = AtomicLong(0L)   // TUN → relay (upload)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var inputStream: FileInputStream
    private lateinit var outputStream: FileOutputStream

    companion object {
        private const val MTU = 32767   // bytes — safe ceiling for a single IP packet read
    }

    // ── Start ─────────────────────────────────────────────────────────────

    fun start() {
        val fd = tunFd.fileDescriptor
        inputStream  = FileInputStream(fd)
        outputStream = FileOutputStream(fd)

        // Loop A — TUN → WebRTC
        scope.launch {
            Log.i(TAG, "TUN→WebRTC loop started")
            val buffer = ByteArray(MTU)
            try {
                while (isActive) {
                    val length = inputStream.read(buffer)
                    if (length <= 0) continue

                    val packet = buffer.copyOf(length)
                    logPacketHeader("TUN→WebRTC", packet)

                    webRtcManager.sendPacket(packet)
                    bytesUp.addAndGet(length.toLong())
                    onBytesChanged(bytesDown.get(), bytesUp.get())
                }
            } catch (e: Exception) {
                if (isActive) Log.e(TAG, "TUN read error", e)
            }
            Log.i(TAG, "TUN→WebRTC loop ended")
        }

        // Loop B stats — emit counters to the service every second
        scope.launch {
            while (isActive) {
                delay(1000)
                onBytesChanged(bytesDown.get(), bytesUp.get())
            }
        }
    }

    // ── Called by WebRtcManager for every incoming DataChannel message ────

    /**
     * Called on the WebRTC callback thread.
     * Writes the raw IP packet to the TUN fd so the OS delivers it to the app that made the request.
     */
    fun onPacketFromRelay(packet: ByteArray) {
        try {
            outputStream.write(packet)
            outputStream.flush()
            bytesDown.addAndGet(packet.size.toLong())
            logPacketHeader("WebRTC→TUN", packet)
        } catch (e: Exception) {
            Log.e(TAG, "TUN write error", e)
        }
    }

    // ── Stop ──────────────────────────────────────────────────────────────

    fun stop() {
        scope.cancel()
        try { inputStream.close() }  catch (_: Exception) {}
        try { outputStream.close() } catch (_: Exception) {}
        Log.i(TAG, "TunPacketForwarder stopped (↓${bytesDown.get()}B ↑${bytesUp.get()}B)")
    }

    // ── Debug helpers ─────────────────────────────────────────────────────

    /**
     * Logs the first 8 bytes of an IP packet — enough to identify IPv4/IPv6,
     * protocol (TCP/UDP/ICMP), and source/dest ports without spamming full payloads.
     */
    private fun logPacketHeader(direction: String, packet: ByteArray) {
        if (!Log.isLoggable(TAG, Log.VERBOSE) || packet.size < 4) return
        val version = (packet[0].toInt() ushr 4) and 0xF
        if (version == 4 && packet.size >= 20) {
            val proto = packet[9].toInt() and 0xFF
            val protoName = when (proto) {
                1  -> "ICMP"
                6  -> "TCP"
                17 -> "UDP"
                else -> "proto=$proto"
            }
            val src = "${packet[12].toInt() and 0xFF}.${packet[13].toInt() and 0xFF}" +
                      ".${packet[14].toInt() and 0xFF}.${packet[15].toInt() and 0xFF}"
            val dst = "${packet[16].toInt() and 0xFF}.${packet[17].toInt() and 0xFF}" +
                      ".${packet[18].toInt() and 0xFF}.${packet[19].toInt() and 0xFF}"
            Log.v(TAG, "$direction IPv4/$protoName $src → $dst (${packet.size}B)")
        } else if (version == 6) {
            Log.v(TAG, "$direction IPv6 (${packet.size}B)")
        }
    }
}
