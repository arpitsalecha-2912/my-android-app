package com.arpit.myapp.net

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID

/** Shared state and the simple wire format for phone-to-phone pranks over the local Wi-Fi. */
object PrankBus {

    const val PORT = 50505
    const val SEP = "\u0001"

    // Commands a phone can send to a buddy (who must have the app + receiver on)
    const val CMD_RING = "RING"
    const val CMD_BUZZ = "BUZZ"
    const val CMD_MSG = "MSG"

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    // A fullscreen message to show, bumped each time one arrives
    private val _incoming = MutableStateFlow<IncomingMsg?>(null)
    val incoming: StateFlow<IncomingMsg?> = _incoming.asStateFlow()

    val myId: String by lazy { UUID.randomUUID().toString().take(8) }

    fun myName(): String =
        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    fun setRunning(b: Boolean) { _running.value = b }

    private val seen = HashMap<String, Peer>()

    @Synchronized
    fun onHello(id: String, name: String, ip: String) {
        if (id == myId) return
        seen[id] = Peer(id, name, ip, System.currentTimeMillis())
        prune()
    }

    @Synchronized
    fun prune() {
        val now = System.currentTimeMillis()
        val it = seen.entries.iterator()
        while (it.hasNext()) if (now - it.next().value.lastSeen > 12_000) it.remove()
        _peers.value = seen.values.sortedBy { it.name }
    }

    @Synchronized
    fun clear() {
        seen.clear()
        _peers.value = emptyList()
    }

    fun postIncoming(from: String, text: String) {
        _incoming.value = IncomingMsg(from, text, System.nanoTime())
    }

    fun consumeIncoming() { _incoming.value = null }

    fun helloPacket(): ByteArray =
        listOf("HELLO", myId, myName()).joinToString(SEP).toByteArray()

    fun cmdPacket(type: String, text: String): ByteArray =
        listOf("CMD", type, myName(), text).joinToString(SEP).toByteArray()
}

data class Peer(val id: String, val name: String, val ip: String, val lastSeen: Long)

data class IncomingMsg(val from: String, val text: String, val stamp: Long)

/** Sends a one-shot command to a specific buddy. Call off the main thread. */
object PrankSender {
    fun send(ip: String, type: String, text: String = "") {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DatagramSocket().use { s ->
                    val data = PrankBus.cmdPacket(type, text)
                    s.send(DatagramPacket(data, data.size, InetAddress.getByName(ip), PrankBus.PORT))
                }
            } catch (_: Exception) { }
        }
    }
}
