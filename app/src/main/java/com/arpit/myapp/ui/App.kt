package com.arpit.myapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arpit.myapp.MainViewModel
import com.arpit.myapp.ScanPhase
import com.arpit.myapp.net.FoundDevice
import com.arpit.myapp.net.NetInfo
import com.arpit.myapp.net.NetworkScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        vm.refreshNet()
        vm.refreshDevice()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) { 0 -> "WiFi Scanner"; 1 -> "This Device"; else -> "Prank Buddies" },
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    if (tab != 2) {
                        IconButton(onClick = { if (tab == 0) vm.refreshNet() else vm.refreshDevice() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text("Network") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    label = { Text("Device") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Face, contentDescription = null) },
                    label = { Text("Prank") },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (tab) {
                0 -> NetworkScreen(vm)
                1 -> DeviceScreen(vm)
                else -> BuddiesScreen()
            }
        }
    }
}

/** One column on phones, 2–3 columns on tablets / landscape. */
@Composable
private fun ResponsiveGrid(content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        modifier = Modifier.widthIn(max = 1200.dp).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

// ======================= NETWORK TAB =======================

@Composable
private fun NetworkScreen(vm: MainViewModel) {
    val sorted = vm.devices.sortedWith(
        compareByDescending<FoundDevice> { it.isGateway }
            .thenByDescending { it.isSelf }
            .thenBy { it.lastOctet }
    )
    ResponsiveGrid {
        fullWidth("wifi") { WifiCard(vm.netInfo, vm.publicIp, onRefresh = vm::refreshNet) }
        fullWidth("scan") { ScanCard(vm) }
        items(sorted, key = { it.ip }) { DeviceCard(it) }
        if (sorted.isNotEmpty() && !vm.scanning) {
            fullWidth("note") {
                Text(
                    "Device types are best guesses from what each device answers. " +
                        "Android doesn't let apps read MAC addresses, so some devices may show only an IP. " +
                        "Phones that are asleep may not answer — scan again to catch them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun WifiCard(info: NetInfo, publicIp: String?, onRefresh: () -> Unit) {
    val ctx = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }
    val hasLocation = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val locationOn = if (Build.VERSION.SDK_INT >= 28) {
        ctx.getSystemService(LocationManager::class.java)?.isLocationEnabled ?: true
    } else true

    SectionCard {
        if (!info.connectedToWifi) {
            Text("📶  Not connected to Wi-Fi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Connect to a Wi-Fi network, then tap refresh.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📶", fontSize = 30.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    info.ssid ?: "Wi-Fi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    signalText(info.rssi),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (info.ssid == null) {
            Spacer(Modifier.height(10.dp))
            if (!hasLocation) {
                FilledTonalButton(onClick = { permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                    Text("Show Wi-Fi name")
                }
                Text(
                    "Android needs Location permission to show the Wi-Fi name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!locationOn) {
                Text(
                    "Turn on Location in quick settings to see the Wi-Fi name, then tap refresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        InfoRow("Your IP", info.ipAddress?.let { ip -> info.prefixLength?.let { "$ip /$it" } ?: ip } ?: "—")
        InfoRow("Router (gateway)", info.gateway ?: "—")
        InfoRow("Public IP", publicIp ?: "…")
        if (info.dns.isNotEmpty()) InfoRow("DNS", info.dns.joinToString(", "))
        info.frequencyMhz?.let { InfoRow("Band", "${bandOf(it)} · $it MHz") }
        info.wifiStandard?.let { InfoRow("Standard", it) }
        info.linkSpeedMbps?.let { InfoRow("Link speed", "$it Mbps") }
        info.bssid?.let { InfoRow("Router MAC (BSSID)", it.uppercase()) }
        info.interfaceName?.let { InfoRow("Interface", it) }
    }
}

@Composable
private fun ScanCard(vm: MainViewModel) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (vm.devices.isEmpty() && !vm.scanning) "Connected devices"
                    else "${vm.devices.size} device${if (vm.devices.size == 1) "" else "s"} connected",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    vm.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = vm::scan, enabled = !vm.scanning) {
                Text(
                    when {
                        vm.scanning -> "Scanning…"
                        vm.devices.isEmpty() -> "Scan"
                        else -> "Scan again"
                    }
                )
            }
        }
        when (vm.phase) {
            ScanPhase.PROBING -> {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
            }
            ScanPhase.NAMING -> {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            ScanPhase.IDLE -> {}
        }
    }
}

@Composable
private fun DeviceCard(d: FoundDevice) {
    val kind = NetworkScanner.kindOf(d)
    val title = when {
        d.name != null -> d.name
        d.hostname != null -> d.hostname
        else -> kind.label
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(kind.emoji, fontSize = 30.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (d.isSelf) Badge("YOU")
                    if (d.isGateway) Badge("ROUTER")
                }
                Text(
                    "${d.ip} · ${kind.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val extra = buildList {
                    d.model?.let { add(it) }
                    if (d.hostname != null && d.hostname != title) add(d.hostname)
                    if (d.openPorts.isNotEmpty()) add("Open: " + d.openPorts.joinToString(", ") { NetworkScanner.PORT_NAMES[it] ?: "$it" })
                    d.responseMs?.let { add("${it} ms") }
                }
                if (extra.isNotEmpty()) {
                    Text(
                        extra.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Spacer(Modifier.width(6.dp))
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun signalText(rssi: Int?): String = when {
    rssi == null -> "Connected"
    rssi >= -55 -> "Excellent signal ($rssi dBm)"
    rssi >= -67 -> "Good signal ($rssi dBm)"
    rssi >= -75 -> "Fair signal ($rssi dBm)"
    else -> "Weak signal ($rssi dBm)"
}

private fun bandOf(mhz: Int): String = when (mhz) {
    in 2400..2500 -> "2.4 GHz"
    in 4900..5900 -> "5 GHz"
    in 5925..7125 -> "6 GHz"
    else -> "${mhz / 1000.0} GHz"
}

// ======================= DEVICE TAB =======================

@Composable
private fun DeviceScreen(vm: MainViewModel) {
    ResponsiveGrid {
        items(vm.sections, key = { it.title }) { s ->
            SectionCard {
                Text(
                    "${s.emoji}  ${s.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                s.rows.forEach { (k, v) -> InfoRow(k, v) }
            }
        }
    }
}

// ======================= SHARED =======================

/** A centered, max-width scrolling column for simple stacked content. */
@Composable
fun AdaptiveColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.55f),
        )
    }
}
