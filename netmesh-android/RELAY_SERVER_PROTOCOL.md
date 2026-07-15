# NetMesh Relay Server Protocol

The relay server is the missing half of the tunnel. The Android app opens a WebRTC DataChannel
to this relay, which then routes the raw IP packets to the real internet and returns responses.

## What the relay must do

```
[ Android device ]
      │  WebRTC DataChannel (binary messages = raw IP packets)
      ▼
[ Relay Server ]
      │  raw IP packets
      ▼
[ TUN/TAP or raw socket on the relay host ]
      │  routed to the internet via the relay host's network interface
      ▼
[ Internet ]
```

## Signaling server (WebSocket)

The relay needs a WebSocket endpoint at the URL you set in `strings.xml`.

Minimal Node.js / Express signaling server:

```javascript
const express = require('express');
const { WebSocketServer } = require('ws');

const app = express();
const server = app.listen(8080);
const wss = new WebSocketServer({ server, path: '/ws' });

wss.on('connection', (clientWs) => {
  console.log('Android client connected');

  // In a production setup you'd have a map of room IDs → relay WebRTC connections.
  // This example assumes one client ↔ one relay peer.

  const relayPeer = createRelayPeerConnection(clientWs);

  clientWs.on('message', (raw) => {
    const msg = JSON.parse(raw);
    switch (msg.type) {
      case 'offer':
        relayPeer.setRemoteDescription({ type: 'offer', sdp: msg.sdp });
        relayPeer.createAnswer().then(answer => {
          relayPeer.setLocalDescription(answer);
          clientWs.send(JSON.stringify({ type: 'answer', sdp: answer.sdp }));
        });
        break;
      case 'candidate':
        relayPeer.addIceCandidate({
          sdpMid: msg.sdpMid,
          sdpMLineIndex: msg.sdpMLineIndex,
          candidate: msg.candidate,
        });
        break;
    }
  });
});
```

## Relay peer (Node.js with node-webrtc)

```javascript
const { RTCPeerConnection, RTCDataChannel } = require('node-datachannel'); // or 'wrtc'

function createRelayPeerConnection(signalingWs) {
  const pc = new RTCPeerConnection({
    iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
  });

  pc.onicecandidate = ({ candidate }) => {
    if (candidate) {
      signalingWs.send(JSON.stringify({
        type: 'candidate',
        sdpMid: candidate.sdpMid,
        sdpMLineIndex: candidate.sdpMLineIndex,
        candidate: candidate.candidate,
      }));
    }
  };

  pc.ondatachannel = ({ channel }) => {
    console.log('DataChannel opened:', channel.label);

    // Create a TUN interface on the relay host using the 'tun2' or 'node-tun' package,
    // or use raw sockets with the 'raw-socket' npm package.
    const tun = createTunInterface('10.0.0.1', '10.0.0.0', 24, 1500);

    // Relay → Android: read IP packets from tun, send over DataChannel
    tun.on('data', (packet) => {
      channel.send(packet);
    });

    // Android → relay: receive IP packets from DataChannel, write to tun
    channel.onmessage = ({ data }) => {
      tun.write(Buffer.from(data));
    };
  };

  return pc;
}
```

## TUN interface on Linux (relay host)

```javascript
// Using the 'node-tun' package: npm install node-tun
const Tun = require('node-tun');

function createTunInterface(localIp, network, prefix, mtu) {
  const tun = new Tun({
    name: 'tun0',
    mtu,
    address: localIp,
    network,
    prefix,
  });
  tun.open();
  // Add a route so the relay OS forwards return traffic to the tun device:
  //   ip route add 10.0.0.0/24 dev tun0
  return tun;
}
```

## NAT on the relay host

The relay host must masquerade outbound packets so return traffic routes correctly:

```bash
# Enable IP forwarding
echo 1 > /proc/sys/net/ipv4/ip_forward

# Masquerade all traffic from the VPN subnet through eth0
iptables -t nat -A POSTROUTING -s 10.0.0.0/24 -o eth0 -j MASQUERADE
iptables -A FORWARD -i tun0 -o eth0 -j ACCEPT
iptables -A FORWARD -i eth0 -o tun0 -m state --state RELATED,ESTABLISHED -j ACCEPT
```

## Summary of IPs

| Role | IP |
|---|---|
| Android device (TUN) | `10.0.0.2` |
| Relay server (TUN) | `10.0.0.1` |
| VPN subnet | `10.0.0.0/24` |
