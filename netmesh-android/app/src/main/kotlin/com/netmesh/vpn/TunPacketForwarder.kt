// TunPacketForwarder.kt में ये बदलाव करें

class TunPacketForwarder(
    private val tunFd: ParcelFileDescriptor,
    private val webRtcManager: WebRtcManager
) {
    @Volatile
    private var isRunning = false
    private val upBytes = AtomicLong(0)
    private val downBytes = AtomicLong(0)

    // नया: फॉरवर्डिंग को सुरक्षित रूप से रोकने के लिए
    fun stopForwarding() {
        isRunning = false
    }

    fun startForwarding() {
        if (isRunning) return
        isRunning = true
        
        val inputStream = FileInputStream(tunFd.fileDescriptor)
        val outputStream = FileOutputStream(tunFd.fileDescriptor)
        val buffer = ByteArray(1500)

        // WebRTC लूप
        Thread {
            try {
                while (isRunning) {
                    val length = inputStream.read(buffer)
                    if (length == -1) break
                    if (length > 0) {
                        upBytes.addAndGet(length.toLong())
                        // सुनिश्चित करें कि यह तभी कॉल हो जब चैनल OPEN हो
                        webRtcManager.sendPacket(buffer.copyOf(length))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // WebRTC -> TUN पाथ
    fun onPacketFromRelay(packet: ByteArray) {
        synchronized(this) {
            val outputStream = FileOutputStream(tunFd.fileDescriptor)
            outputStream.write(packet)
            downBytes.addAndGet(packet.size.toLong())
        }
    }
}
