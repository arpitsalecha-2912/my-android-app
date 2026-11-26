package com.arpit.myapp.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.resume

/** Finds device names that phones, TVs, printers, Macs etc. announce on the network (mDNS / Bonjour). */
object Mdns {

    data class Hit(val ip: String, val name: String, val type: String, val model: String?)

    private val TYPES = listOf(
        "_googlecast._tcp", "_androidtvremote2._tcp", "_airplay._tcp", "_raop._tcp",
        "_companion-link._tcp", "_ipp._tcp", "_printer._tcp", "_smb._tcp",
        "_workstation._tcp", "_spotify-connect._tcp", "_hap._tcp", "_http._tcp",
        "_amzn-wplay._tcp", "_device-info._tcp",
    )

    suspend fun discover(ctx: Context, durationMs: Long): List<Hit> {
        val nsd = ctx.getSystemService(NsdManager::class.java) ?: return emptyList()
        val found = ConcurrentLinkedQueue<NsdServiceInfo>()

        val started = mutableListOf<NsdManager.DiscoveryListener>()
        for (type in TYPES) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onServiceFound(info: NsdServiceInfo) { found.add(info) }
                override fun onServiceLost(info: NsdServiceInfo) {}
            }
            try {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                started += listener
            } catch (_: Exception) { }
        }

        delay(durationMs)
        for (l in started) try { nsd.stopServiceDiscovery(l) } catch (_: Exception) { }

        val hits = mutableListOf<Hit>()
        val seen = HashSet<String>()
        for (s in found) {
            if (!seen.add(s.serviceName + "|" + s.serviceType)) continue
            val r = withTimeoutOrNull(2500) { resolve(nsd, s) } ?: continue
            @Suppress("DEPRECATION")
            val host = r.host
            if (host !is Inet4Address) continue
            val ip = host.hostAddress ?: continue
            val attrs = try { r.attributes } catch (_: Exception) { emptyMap() }
            val friendly = attrs["fn"]?.let { String(it) }?.takeIf { it.isNotBlank() }
            val model = (attrs["md"] ?: attrs["model"] ?: attrs["ty"])?.let { String(it) }?.takeIf { it.isNotBlank() }
            hits += Hit(
                ip = ip,
                name = friendly ?: cleanName(r.serviceName),
                type = s.serviceType.trim('.'),
                model = model,
            )
        }
        return hits
    }

    private fun cleanName(n: String): String =
        // e.g. "Chromecast-1a2b3c4d5e..." -> keep short
        if (n.length > 40) n.take(40) + "…" else n

    private suspend fun resolve(nsd: NsdManager, s: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { cont ->
            try {
                @Suppress("DEPRECATION")
                nsd.resolveService(s, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        if (cont.isActive) cont.resume(info)
                    }
                })
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
}
