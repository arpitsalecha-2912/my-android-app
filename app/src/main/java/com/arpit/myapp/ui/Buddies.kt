package com.arpit.myapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arpit.myapp.PrankService
import com.arpit.myapp.net.Peer
import com.arpit.myapp.net.PrankBus
import com.arpit.myapp.net.PrankSender

@Composable
fun BuddiesScreen() {
    val ctx = LocalContext.current
    val running by PrankBus.running.collectAsState()
    val peers by PrankBus.peers.collectAsState()
    var pending by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted || Build.VERSION.SDK_INT < 33) PrankService.start(ctx)
        pending = false
    }

    fun toggle(on: Boolean) {
        if (on) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                pending = true
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                PrankService.start(ctx)
            }
        } else {
            PrankService.stop(ctx)
        }
    }

    AdaptiveColumn {
        // Receiver control
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Prank receiver", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (running) "ON — buddies can reach this phone" else "OFF — turn on to join and be reachable",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = running || pending, onCheckedChange = { toggle(it) })
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Text("You appear as", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(PrankBus.myName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }
        }

        // Buddy list
        if (peers.isEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👋", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (running) "Looking for buddies…" else "No buddies yet",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Your friend needs this same app, their receiver switched ON, and both phones on the same Wi-Fi. " +
                            "They'll pop up here within a few seconds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            peers.forEach { BuddyCard(it) }
        }

        Text(
            "Only phones running this app with the receiver on can be pranked — it can't touch a phone that doesn't have it. All in good fun 😄",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun BuddyCard(peer: Peer) {
    var showMsg by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📱", fontSize = 26.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(peer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(peer.ip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { PrankSender.send(peer.ip, PrankBus.CMD_RING) }, modifier = Modifier.weight(1f)) { Text("🔊 Ring") }
                FilledTonalButton(onClick = { PrankSender.send(peer.ip, PrankBus.CMD_BUZZ) }, modifier = Modifier.weight(1f)) { Text("📳 Buzz") }
                FilledTonalButton(onClick = { showMsg = true }, modifier = Modifier.weight(1f)) { Text("💬 Msg") }
            }
        }
    }

    if (showMsg) {
        var textVal by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMsg = false },
            title = { Text("Send a popup to ${peer.name}") },
            text = {
                OutlinedTextField(
                    value = textVal,
                    onValueChange = { textVal = it.take(80) },
                    placeholder = { Text("e.g. Behind you! 👻") },
                    singleLine = false,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    PrankSender.send(peer.ip, PrankBus.CMD_MSG, textVal.ifBlank { "GOT YOU! 😄" })
                    showMsg = false
                }) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { showMsg = false }) { Text("Cancel") } },
        )
    }
}
