# Android Studio Quickstart

## 1. Open the project

```
File → Open → select netmesh-android/
```

Wait for Gradle sync to finish (may take 3-5 minutes on first open — it downloads the WebRTC AAR).

## 2. Configure your signaling server URL

Edit `app/src/main/res/values/strings.xml`:
```xml
<string name="signaling_server_url">wss://your-signaling-server.example.com/ws</string>
```

## 3. Connect a physical Android device

- Enable Developer Options → USB Debugging on the device
- Plug in via USB — the device appears in the "Running Devices" toolbar

> **VpnService does NOT work reliably on Android emulators.**
> The VPN permission dialog may not appear, and packet routing doesn't work.
> Always test on a real device.

## 4. Build and run

- Press the green ▶ Run button
- First launch: Android will show **"NetMesh wants to set up a VPN connection"** — tap **OK**
- The app starts connecting; check `adb logcat -s NetMesh` for detailed logs

## 5. Verify traffic flows

```bash
# Stream VPN-related logs in real time
adb logcat -s NetMesh/VpnService NetMesh/WebRtc NetMesh/TunForwarder NetMesh/Signaling

# While connected, run a curl on the device via adb shell and confirm packets appear in logs
adb shell curl -s https://api.ipify.org   # should show the relay server's IP, not the device's
```

## 6. Common issues

| Symptom | Fix |
|---|---|
| `establish() returned null` | VPN permission was denied or revoked — tap Connect again and allow |
| DataChannel never opens | Check signaling server URL; check that the relay peer is listening |
| ICE connection failed | Add a TURN server in `WebRtcManager.kt` (STUN alone fails behind symmetric NAT) |
| Build fails: `io.github.webrtc-sdk:android` not found | Ensure `mavenCentral()` is in the repositories block in `app/build.gradle` |
| `VpnService$Builder.establish()` on API < 26 | `minSdk` is set to 26; the build will refuse older targets |

## 7. Adding a TURN server (for reliable NAT traversal)

In `NetMeshVpnService.kt`, find the `iceServers` list and add:

```kotlin
PeerConnection.IceServer.builder("turn:your-turn-server.example.com:3478")
    .setUsername("your-turn-username")
    .setPassword("your-turn-password")
    .createIceServer()
```

Free TURN servers for testing: [Open Relay](https://www.metered.ca/tools/openrelay/)
Production: deploy [coturn](https://github.com/coturn/coturn) on your relay host.

## 8. Release signing

```
Build → Generate Signed Bundle / APK → APK → create or choose a keystore → Release
```

The signed APK appears at `app/release/app-release.apk`.
Side-load it:
```bash
adb install app/release/app-release.apk
```
