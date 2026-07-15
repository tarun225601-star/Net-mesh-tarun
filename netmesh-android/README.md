# NetMesh Android — Native VPN Reference Implementation

## What this is

A complete native Android app that:
1. Uses Android's `VpnService` API to create a TUN interface capturing **all device IP traffic**
2. Forwards those raw IP packets over a **WebRTC DataChannel** to a relay/exit peer
3. Receives response packets from the relay and writes them back to the TUN interface
4. Runs as a **Foreground Service** so the OS doesn't kill it in the background

This is production-quality reference code. Build it in **Android Studio** (Replit cannot compile/sign Android native apps).

## Prerequisites

- Android Studio Hedgehog or newer
- Android SDK 26+
- A NetMesh signaling server (WebSocket, see `SignalingClient.kt`)
- A NetMesh relay/exit server that speaks the same WebRTC DataChannel protocol

## Build Steps

1. Open Android Studio → `File → Open` → select the `netmesh-android/` folder
2. Let Gradle sync
3. Edit `app/src/main/res/values/strings.xml` → set `signaling_server_url` to your actual WebSocket URL
4. Build → Run on a physical device (VpnService requires real hardware or a properly configured emulator)
5. Tap **Connect**, grant the VPN permission dialog, and watch `adb logcat -s NetMesh` for traffic logs

## Architecture

```
[ Device apps ]
      │  all IP traffic (TCP/UDP/ICMP)
      ▼
[ TUN interface  (10.0.0.2/24) ]   ← created by NetMeshVpnService via VpnService.Builder
      │  raw IP packets (byte arrays)
      ▼
[ TunPacketForwarder ]
  - reads loop: FileInputStream(tunFd) → ByteArray
  - write loop: ByteArray → FileOutputStream(tunFd)
      │
      ▼
[ WebRtcManager ]
  - PeerConnection + DataChannel (binary, ordered=false for UDP-like perf)
  - Ice/SDP exchange via SignalingClient (WebSocket)
      │  DataChannel binary messages = raw IP packets
      ▼
[ Relay Server ]  (your exit node — routes packets to the real internet)
```

## Key Files

| File | Purpose |
|---|---|
| `NetMeshVpnService.kt` | Core VpnService — builds TUN interface, owns lifecycle |
| `WebRtcManager.kt` | PeerConnection + DataChannel setup and management |
| `TunPacketForwarder.kt` | Two coroutine loops bridging TUN ↔ DataChannel |
| `SignalingClient.kt` | WebSocket-based SDP/ICE signaling with your server |
| `MainActivity.kt` | UI — connect/disconnect button, VPN permission flow |
| `AndroidManifest.xml` | VPN + foreground service permissions |

## Protocol

Each DataChannel message = one raw IP packet (no framing needed — DataChannel is message-oriented).

- TUN → DataChannel: read one IP packet, `dataChannel.send(ByteBuffer.wrap(packet))`
- DataChannel → TUN: `onMessage` callback → write bytes to TUN fd

## Signaling Protocol (WebSocket messages, JSON)

```json
// Client → Server: offer
{ "type": "offer", "sdp": "..." }

// Server → Client: answer
{ "type": "answer", "sdp": "..." }

// Both directions: ICE candidates
{ "type": "candidate", "sdpMid": "0", "sdpMLineIndex": 0, "candidate": "..." }
```
