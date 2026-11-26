package com.arpit.myapp

import android.app.Application
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arpit.myapp.info.DeviceInfo
import com.arpit.myapp.info.InfoSection
import com.arpit.myapp.net.FoundDevice
import com.arpit.myapp.net.Mdns
import com.arpit.myapp.net.NetInfo
import com.arpit.myapp.net.NetworkScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class ScanPhase { IDLE, PROBING, NAMING }

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    // Network tab
    var netInfo by mutableStateOf(NetInfo())
        private set
    var publicIp by mutableStateOf<String?>(null)
        private set
    val devices = mutableStateListOf<FoundDevice>()
    var phase by mutableStateOf(ScanPhase.IDLE)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var status by mutableStateOf("Tap Scan to find devices on this Wi-Fi")
        private set

    // Device tab
    var sections by mutableStateOf<List<InfoSection>>(emptyList())
        private set

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val scanning: Boolean get() = phase != ScanPhase.IDLE

    fun refreshNet() {
        viewModelScope.launch {
            netInfo = withContext(Dispatchers.IO) { NetworkScanner.readNetInfo(app) }
            publicIp = if (netInfo.connectedToWifi) withContext(Dispatchers.IO) { NetworkScanner.fetchPublicIp() } else null
        }
    }

    fun refreshDevice() {
        viewModelScope.launch {
            sections = withContext(Dispatchers.Default) { DeviceInfo.collect(app) }
        }
    }

    private fun update(ip: String, f: (FoundDevice) -> FoundDevice) {
        val i = devices.indexOfFirst { it.ip == ip }
        if (i >= 0) devices[i] = f(devices[i])
    }

    fun scan() {
        if (scanning) return
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { NetworkScanner.readNetInfo(app) }
            netInfo = info
            val myIp = info.ipAddress
            if (!info.connectedToWifi || myIp == null) {
                status = "Connect to a Wi-Fi network first"
                return@launch
            }

            phase = ScanPhase.PROBING
            progress = 0f
            devices.clear()
            val started = SystemClock.elapsedRealtime()
            val base = myIp.substringBeforeLast('.')

            // Listen for device names in the background while we probe
            val mdns = ioScope.async { Mdns.discover(app, 9000) }

            val hosts = NetworkScanner.hostsToScan(myIp)
            status = "Checking ${hosts.size} addresses…"
            val sem = Semaphore(48)
            var done = 0
            hosts.map { h ->
                launch {
                    val r = withContext(Dispatchers.IO) { sem.withPermit { NetworkScanner.probe(h) } }
                    done++
                    progress = done.toFloat() / hosts.size
                    if (r != null) {
                        devices.add(
                            FoundDevice(
                                ip = h,
                                openPorts = r.ports,
                                responseMs = r.ms,
                                isSelf = h == myIp,
                                isGateway = h == info.gateway,
                            )
                        )
                        status = "Found ${devices.size} so far…"
                    }
                }
            }.joinAll()

            if (devices.none { it.ip == myIp }) devices.add(FoundDevice(ip = myIp, isSelf = true))
            update(myIp) { it.copy(name = "${Build.MANUFACTURER.replaceFirstChar { c -> c.uppercase() }} ${Build.MODEL}") }

            // Names: reverse DNS (from the router) + mDNS announcements
            phase = ScanPhase.NAMING
            status = "Looking up device names…"
            devices.toList().filter { !it.isSelf }.map { d ->
                launch {
                    val job = ioScope.async { NetworkScanner.reverseDns(d.ip) }
                    val hn = withTimeoutOrNull(3000) { job.await() }
                    if (hn != null) update(d.ip) { it.copy(hostname = hn) }
                }
            }.joinAll()

            val hits = withTimeoutOrNull(20_000) { mdns.await() } ?: emptyList()
            for (hit in hits) {
                if (hit.ip.substringBeforeLast('.') != base) continue
                if (devices.none { it.ip == hit.ip }) {
                    devices.add(FoundDevice(ip = hit.ip, isGateway = hit.ip == info.gateway))
                }
                update(hit.ip) {
                    it.copy(
                        name = if (it.isSelf) it.name else (it.name ?: hit.name),
                        model = it.model ?: hit.model,
                        services = (it.services + hit.type).distinct(),
                    )
                }
            }

            val secs = (SystemClock.elapsedRealtime() - started) / 1000
            status = "Scan finished in ${secs}s"
            phase = ScanPhase.IDLE
        }
    }

    override fun onCleared() {
        super.onCleared()
        ioScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
