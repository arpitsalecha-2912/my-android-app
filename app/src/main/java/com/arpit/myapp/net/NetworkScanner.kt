package com.arpit.myapp.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

data class NetInfo(
    val connectedToWifi: Boolean = false,
    val ssid: String? = null,
    val bssid: String? = null,
    val ipAddress: String? = null,
    val prefixLength: Int? = null,
    val gateway: String? = null,
    val dns: List<String> = emptyList(),
    val linkSpeedMbps: Int? = null,
    val rssi: Int? = null,
    val frequencyMhz: Int? = null,
    val wifiStandard: String? = null,
    val interfaceName: String? = null,
)

data class FoundDevice(
    val ip: String,
    val hostname: String? = null,
    val name: String? = null,
    val model: String? = null,
    val openPorts: List<Int> = emptyList(),
    val services: List<String> = emptyList(),
    val responseMs: Long? = null,
    val isGateway: Boolean = false,
    val isSelf: Boolean = false,
) {
    val lastOctet: Int get() = ip.substringAfterLast('.').toIntOrNull() ?: 0
}

data class DeviceKind(val emoji: String, val label: String)

object NetworkScanner {

    val PORT_NAMES = mapOf(
        80 to "HTTP", 443 to "HTTPS", 22 to "SSH", 445 to "SMB", 139 to "NetBIOS",
        53 to "DNS", 554 to "RTSP (camera)", 8008 to "Cast", 8080 to "HTTP-alt",
        9100 to "Printer", 62078 to "Apple sync", 7000 to "AirPlay",
    )
    private val ALL_PORTS = listOf(80, 443, 62078, 8008, 445, 22, 554, 9100, 8080, 7000, 139, 53)
    private val QUICK_PORTS = listOf(80, 443, 62078, 445, 8008)

    // ---------- Wi-Fi details ----------

    @Suppress("DEPRECATION")
    fun readNetInfo(ctx: Context): NetInfo {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return NetInfo()
        val wifiNet = cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return NetInfo()

        val lp = cm.getLinkProperties(wifiNet)
        val la = lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }
        val gw = lp?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
        val dns = lp?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()

        val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
        val wi = try { wm?.connectionInfo } catch (_: Exception) { null }

        val ssid = wi?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        val bssid = wi?.bssid?.takeIf { it != "02:00:00:00:00:00" }
        val standard = if (Build.VERSION.SDK_INT >= 30 && wi != null) {
            when (wi.wifiStandard) {
                1 -> "Legacy (a/b/g)"
                4 -> "Wi-Fi 4 (802.11n)"
                5 -> "Wi-Fi 5 (802.11ac)"
                6 -> "Wi-Fi 6 (802.11ax)"
                7 -> "WiGig (802.11ad)"
                8 -> "Wi-Fi 7 (802.11be)"
                else -> null
            }
        } else null

        return NetInfo(
            connectedToWifi = true,
            ssid = ssid,
            bssid = bssid,
            ipAddress = la?.address?.hostAddress,
            prefixLength = la?.prefixLength,
            gateway = gw,
            dns = dns,
            linkSpeedMbps = wi?.linkSpeed?.takeIf { it > 0 },
            rssi = wi?.rssi?.takeIf { it in -127..-1 },
            frequencyMhz = wi?.frequency?.takeIf { it > 0 },
            wifiStandard = standard,
            interfaceName = lp?.interfaceName,
        )
    }

    fun fetchPublicIp(): String? = try {
        val c = URL("https://api.ipify.org").openConnection() as HttpURLConnection
        c.connectTimeout = 4000
        c.readTimeout = 4000
        c.inputStream.bufferedReader().use { it.readText().trim() }.takeIf { it.length in 7..45 }
    } catch (_: Exception) { null }

    // ---------- Scanning ----------

    fun hostsToScan(myIp: String): List<String> {
        val base = myIp.substringBeforeLast('.')
        return (1..254).map { "$base.$it" }
    }

    data class ProbeResult(val ports: List<Int>, val ms: Long?)

    private enum class PortState { OPEN, CLOSED, NONE }

    private fun tryPort(addr: InetAddress, port: Int, timeoutMs: Int): PortState = try {
        Socket().use { it.connect(InetSocketAddress(addr, port), timeoutMs) }
        PortState.OPEN
    } catch (e: ConnectException) {
        // "Connection refused" means a device answered -> it exists
        if (e.message?.contains("ECONNREFUSED") == true) PortState.CLOSED else PortState.NONE
    } catch (_: Exception) {
        PortState.NONE
    }

    /** Returns null when nothing answers at this address. Blocking: call on IO. */
    fun probe(ip: String): ProbeResult? {
        val addr = try { InetAddress.getByName(ip) } catch (_: Exception) { return null }
        var alive = false
        var ms: Long? = null
        val t0 = SystemClock.elapsedRealtime()
        try {
            if (addr.isReachable(700)) {
                alive = true
                ms = SystemClock.elapsedRealtime() - t0
            }
        } catch (_: Exception) { }

        val open = mutableListOf<Int>()
        val first = if (alive) ALL_PORTS else QUICK_PORTS
        for (p in first) {
            when (tryPort(addr, p, if (alive) 300 else 250)) {
                PortState.OPEN -> { open += p; alive = true }
                PortState.CLOSED -> alive = true
                PortState.NONE -> {}
            }
        }
        if (alive && first === QUICK_PORTS) {
            for (p in ALL_PORTS - QUICK_PORTS.toSet()) {
                if (tryPort(addr, p, 300) == PortState.OPEN) open += p
            }
        }
        return if (alive) ProbeResult(open.sorted(), ms) else null
    }

    fun reverseDns(ip: String): String? = try {
        val h = InetAddress.getByName(ip).canonicalHostName
        if (h == null || h == ip) null else h
    } catch (_: Exception) { null }

    // ---------- Guessing what a device is ----------

    fun kindOf(d: FoundDevice): DeviceKind {
        val s = d.services.joinToString(" ")
        val p = d.openPorts
        return when {
            d.isSelf -> DeviceKind("📱", "This phone")
            d.isGateway -> DeviceKind("📡", "Router")
            "androidtvremote" in s -> DeviceKind("📺", "Android TV")
            "googlecast" in s || 8008 in p -> DeviceKind("📺", "Smart TV / Chromecast")
            "amzn-wplay" in s -> DeviceKind("📺", "Fire TV")
            "companion-link" in s || 62078 in p -> DeviceKind("🍎", "iPhone / iPad")
            "airplay" in s || "raop" in s || 7000 in p -> DeviceKind("🍎", "Apple TV / Mac")
            "ipp" in s || "printer" in s || 9100 in p -> DeviceKind("🖨️", "Printer")
            554 in p -> DeviceKind("📷", "Camera")
            "hap" in s -> DeviceKind("🏠", "Smart home device")
            "spotify" in s -> DeviceKind("🔊", "Speaker")
            "smb" in s || "workstation" in s || 445 in p || 139 in p -> DeviceKind("💻", "Computer / NAS")
            22 in p -> DeviceKind("🖥️", "Server / Linux")
            80 in p || 443 in p || 8080 in p -> DeviceKind("🔌", "Smart / IoT device")
            else -> DeviceKind("📱", "Phone or device")
        }
    }
}
