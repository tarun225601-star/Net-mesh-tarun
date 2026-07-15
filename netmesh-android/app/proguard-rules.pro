# WebRTC — keep all classes; the library uses reflection internally
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# OkHttp / OkIO
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep VPN service and receivers so Android can instantiate them by name
-keep class com.netmesh.vpn.NetMeshVpnService { *; }
-keep class com.netmesh.vpn.BootReceiver { *; }

# Keep R (resource IDs used in Kotlin code)
-keepclassmembers class **.R$* {
    public static <fields>;
}
